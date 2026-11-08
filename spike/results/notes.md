# Phase 1 spike notes (raw; summarized into docs/plans/phase-1-notes.md in Task 10)

## Inputs (Task 6)
- `generate.py --seed 20261108`: large.csv 9,900,000 rows, 1,079,059,385 bytes (1.005 GiB), 20 columns; generated in 16 s on the host. Awkward set: 18 files (6 generated + 12 golden edge copies).

## App on Spark Connect (Task 2)
- Gradle 9.9.0 wrapper (9.8.2's GitHub download answered 503; the wrapper's Java downloader kept failing against GitHub's CDN, so the zip was fetched with curl and verified against the published SHA-256). Kotlin 2.3.21, Spring Boot 4.1.2, JDK 21 toolchain, `spark-connect-client-jvm_2.13:4.3.0`.
- Conflict 1 — logging: the Spark client brings `log4j-slf4j2-impl`, Spring Boot brings `log4j-to-slf4j`; log4j refuses both ("log4j-slf4j2-impl cannot be present with log4j-to-slf4j"). Fix: exclude `log4j-slf4j2-impl` from the client. `log4j-core` must stay: the client loads `org.apache.logging.log4j.core.Filter` via `SparkClassUtils.getCompanionObject` (NoClassDefFoundError without it).
- Jackson: Spark asks for 2.21.2 (incl. jackson-module-scala_2.13), Spring Boot manages 2.21.7; aligned without errors.
- JVM flag needed: `--add-opens=java.base/java.nio=ALL-UNNAMED`. Without it any data transfer fails with "UnsupportedOperationException: sun.misc.Unsafe or java.nio.DirectByteBuffer.<init>(long, int) not available" (Arrow on JDK 21); `spark.version()` alone works. The app's run configuration and image need it too.
- SparkConnectSmokeIT: 2/2, suite time="2.209" (context start + session + range(1000).count()).

## One Scala API, two sessions (Task 3)
- `rules` compiles against `spark-sql-api_2.13:4.3.0` only; the same JUnit 5 tests run on a classic local session (`testClassic`, runtime `spark-sql`) and over Connect (`testConnect`, runtime `spark-connect-client-jvm`), picked by `spark.api` through `SparkSession.builder()`: `.master("local[2]")` vs `.remote(url)`.
- Window function (`row_number` over partition, ties by column index), `cache()`, `localCheckpoint()` and `groupBy/count`: identical results on both (2/2 each).
- Reliable `checkpoint()`: fails on both sides with "Checkpoint directory has not been set in the SparkContext" until `spark.checkpoint.dir` is set when the SparkContext starts. Classic: `.config("spark.checkpoint.dir", dir)` on the builder. Connect: server configuration only (`--conf spark.checkpoint.dir=s3a://checkpoints/spark` on the Connect server), and on a cluster it must be shared storage (S3A here). With S3A the bucket must exist before the Connect server starts, or SparkContext init fails with `UnknownStoreException`/`NoSuchBucket`. `localCheckpoint()` needs no setup on either side.
- Classic sessions on JDK 21 need Spark's usual `--add-opens` list (JavaModuleOptions); Connect needs only `java.nio`.
- Gradle 9 fails a test task that discovers no tests (`failOnNoDiscoveredTests`).

## PostgreSQL over JDBC, run tags, cancel (Task 4)
- App side: `COPY ... FROM STDIN` through pgjdbc `CopyManager` with rows generated on the fly: 1 M rows in ~0.8 s, heap 66–111 MiB.
- Spark side over Connect: JDBC read with `partitionColumn=row_no`, bounds from `min/max`, `numPartitions=4` (the scan stage ran 4 tasks), `groupBy`, JDBC write with `reWriteBatchedInserts=true`: 0.41–0.44 s warm, 1.3 s cold, for 1 M rows.
- `--packages` jars are not visible to `DriverManager` on the Connect server: without `.option("driver", "org.postgresql.Driver")` Spark fails with "No suitable driver".
- Executors must get the Compose address (`postgres:5432`): the same round trip with the app's `localhost` URL fails with a connection error (tested).
- Run tags: `addTag("run-<id>")` is per thread in the client; jobs carry `SparkConnect_SessionTag_User_<user>_Session_<session>_Tag_run-<id>` in the driver UI/REST API, so a run's jobs can be found by the `_Tag_run-<id>` suffix. The History Server lists the (running) Connect application.
- Cancel: `interruptTag("run-cancel")` from another thread stops a job of > 60 s within the 10 s budget; no job with the tag keeps running.

## Resumable uploads (Task 5)
- tus 1.0 with tus-java-server 1.0.0-3.3 in Spring Boot 4.1 (Jakarta Servlet 6, no conflicts): chunks land on local disk, and when the last one arrives the controller streams the file once into the `UploadSink`, then deletes tus's copy.
- 1 GiB (`large.csv`) in 8 MiB chunks, with the connection dropped mid-chunk (raw socket: headers for 8 MiB, half the bytes, hang-up) at ~50 % and ~99 %: resumes and arrives byte-identical (SHA-256), sink called exactly once; a re-sent final chunk is refused. 8.2–8.6 s on loopback (~125 MB/s incl. hashing), peak heap ~130 MiB with `-Xmx256m` (app, client and test in one JVM).
- tus-java-client sends chunks as POST with `X-HTTP-Method-Override: PATCH` (HttpURLConnection cannot PATCH): server-side code that reacts to completed PATCHes must honour the override. Its `finish()` closes the TusUpload's stream: a resume needs a fresh TusUpload (same fingerprint).
- Storing synchronously in the last PATCH makes the client wait for the store (seconds for 1 GiB); the real app should hand off to a background job.

## Original storage: large object vs object store (Task 7, candidate 1)
- Both round-trip `small.csv` and the 1 GiB `large.csv` byte-identical (SHA-256), 3 runs each, in a 256 MiB heap.
- PostgreSQL large object (pgjdbc `LargeObjectManager`, one transaction): 1 GiB store 14.5–15.3 s, read back 5.6–6.3 s; disk growth 480 MB for 1,079 MB (the 2 KB `pg_largeobject` pages are compressed, ~2.2×). Deleting needs `lo_unlink` + `VACUUM pg_largeobject` to give space back.
- Garage over S3 (AWS SDK v2 2.35.4, streamed multipart, 8 MiB parts): 1 GiB store 8.0–8.1 s, read back 3.5–3.6 s; disk growth 199 MB (zstd, ~5.4×). Runs 2–3 grew 0 bytes: Garage deduplicates blocks and collects deleted ones lazily.
- AWS SDK v2 ≥ 2.30 sends flexible checksums by default; Garage rejects them ("Invalid payload signature"). Fix: `requestChecksumCalculation(WHEN_REQUIRED)` and `responseChecksumValidation(WHEN_REQUIRED)`. Spark's S3A may need the equivalent (Task 9).
- Garage's image has no shell tools: disk use measured from a throwaway container mounting its volume.
- Peak heap is summed over heap pools (garbage included): 249 MiB for LO read-back and S3 store, under the 256 MiB cap without OOM — memory stays bounded, but the 8 MiB part buffers and pgjdbc's LO stream buffering are where it goes.
- Candidate 1 (original only, as a large object): Spark cannot read a large object in parallel — it would have to be unpacked into rows or a file before each run, which is exactly candidate 2/3/4's preparation. So candidate 1 is "original storage only", decided between LO and S3 here, and combined with one of candidates 2–4 for Spark.

## Rows in PostgreSQL: shared table vs table per upload (Task 8, candidates 2 and 3)
- App parser: FastCSV 4.4.0 (RFC 4180, BOM dropped by hand, quoted line breaks, CRLF). COPY ... FORMAT csv with every field quoted, so empty cells stay empty strings (unquoted empty = NULL in COPY).
- Correctness: both candidates give back exactly the rows the app parsed (multiset, column names included) for `small.csv` and all 18 awkward files: 500 columns, odd names (spaces, dots, quotes, Polish letters, 70 characters), a quoted line break, BOM + CRLF, empty cells, the golden edge inputs. Candidate 3 uses positional column names (`c0`…), so names never matter; it cannot hold more than 1,599 data columns (PostgreSQL's 1,600-column limit, `wide-2000.csv` fails at CREATE TABLE). Candidate 2 has no such limit.
- The JDBC read splits into exactly the requested partitions (4 tasks for 4 partitions) with bounds from `min/max(row_no)`.
- 1 GiB (`large.csv`, 9.9 M rows × 20 columns) from the stored large object, medians of 3 runs:

| | prepare (read LO, parse, COPY) | Spark `count()` | Spark `dropDuplicates().count()` | disk (table + index) |
|---|---|---|---|---|
| candidate 2: `dataset_rows(dataset_id, row_no, vals text[])` | 32.5 s | 7.1 s | 58.0 s | 3.02 GB (clean run) |
| candidate 3: `ds_<id>(row_no, c0 … c19)` | 24.6 s | 4.4 s | 47.7 s | 1.71 GB |

- Candidate 2's arrays cost space (array header and per-element lengths) and its deletes leave bloat: after deleting and vacuuming every dataset the primary-key index still held 596 MB; it needs `REINDEX`/`VACUUM FULL` from time to time. Candidate 3's `DROP TABLE` frees everything immediately.
- Peak app heap during preparation: 121–141 MiB (streaming parse + COPY), Spark reads ~40 MiB in the app.
- Disk: the 1 GiB dataset as rows plus its original (0.48 GB) plus WAL is several GB; the first full run filled the Colima VM disk (98 GB, mostly other images and build cache) and crashed PostgreSQL ("No space left on device"). Rerun after resetting the spike's database and pruning build cache, with `max_wal_size=1GB`.

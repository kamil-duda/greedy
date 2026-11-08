# Phase 1 notes

Findings of the [Phase 1 spike](phase-1.md), run 2026-11-08 on the owner's MacBook (Colima VM: 4 CPUs, 8 GB, aarch64). The spike code is deleted; the raw measurements are in [phase-1-data/measurements.tsv](phase-1-data/measurements.tsv), the execution record in the git history (`chore(spike): …` commits).

## Summary

| Question | Answer |
|---|---|
| Spark Connect or `spark-submit` per run? | **Spark Connect works** for everything tested: Spring Boot 4.1.2 + client 4.3.0 on JDK 21, parallel JDBC reads and writes, run tags visible in the UIs, cancel by tag, the same Scala code on classic and Connect sessions. No fallback needed. |
| Upload protocol | **tus 1.0** (tus-java-server 1.0.0-3.3): 1 GiB with two mid-chunk drops arrives byte-identical in a 256 MiB heap. |
| Where originals live | **PostgreSQL large objects** (owner, 2026-11-08: PostgreSQL only). Garage was measured faster and smaller (below) but adds a second store. |
| Rows for Spark | Candidate 2 (one shared table) or 3 (table per upload): **owner's decision**, numbers below. Candidate 1 is "original storage only"; candidate 4 (object store) was dropped with Garage. |

## Spark Connect and Spring Boot

- Gradle 9.9.0, Kotlin 2.3.21, Spring Boot 4.1.2, `spark-connect-client-jvm_2.13:4.3.0`, JDK 21; the Connect server is the official `apache/spark:4.3.0-scala2.13-java21-ubuntu` image with the PostgreSQL driver added via `--packages`.
- Logging conflict: the client brings `log4j-slf4j2-impl`, Spring Boot `log4j-to-slf4j`; log4j refuses both. Exclude `log4j-slf4j2-impl` from the client. Keep `log4j-core`: the client loads `org.apache.logging.log4j.core.Filter` at runtime.
- Jackson aligns: Spark asks for 2.21.2 (incl. `jackson-module-scala_2.13`), Spring Boot manages 2.21.7.
- JVM flag needed by the app: `--add-opens=java.base/java.nio=ALL-UNNAMED` (Arrow). Without it any data transfer fails with "sun.misc.Unsafe or java.nio.DirectByteBuffer.<init>(long, int) not available". Classic local sessions (tests) need Spark's whole `--add-opens` list.
- One Scala API, two sessions: code compiled against `spark-sql-api` only (window function with a column-index tie-break, `cache`, `localCheckpoint`, `groupBy/count`) gives identical results on a classic local session and over Connect. `SparkSession.builder()` picks the implementation: `.master(…)` vs `.remote(…)`.
- Reliable `checkpoint()` needs `spark.checkpoint.dir` set when the SparkContext starts: on the builder for classic sessions, as Connect **server** configuration for Connect, and on a cluster on shared storage. `localCheckpoint()` needs no setup on either side. With PostgreSQL only, Phase 3 should rely on `localCheckpoint()`; shared checkpoint storage is a Spark-side concern for a future cluster.

## PostgreSQL over JDBC, tags, cancel

- App side: `COPY … FROM STDIN` through pgjdbc `CopyManager`, rows generated or parsed on the fly: 1 M rows in 0.8 s.
- Spark side: JDBC read with `partitionColumn=row_no`, bounds from `min/max(row_no)`, `numPartitions` gives exactly that many scan tasks; write with `reWriteBatchedInserts=true`. 1 M rows read, grouped and written back in 0.44 s warm.
- On the Connect server, jars from `--packages` are invisible to `DriverManager`: set `driver=org.postgresql.Driver` on every JDBC read and write ("No suitable driver" otherwise).
- Executors must get the Compose address (`postgres:5432`); the app's `localhost` URL fails with a connection error (tested). Addresses are configuration on both sides.
- Run tags: `addTag("run-<id>")` applies to operations started by the calling thread; jobs show `SparkConnect_SessionTag_User_<user>_Session_<session>_Tag_run-<id>` in the driver UI and REST API. `interruptTag` from another thread stops a job of > 60 s within 10 s.

## Uploads (tus)

- tus-java-server 1.0.0-3.3 runs on Spring Boot 4.1 (Jakarta Servlet 6) without conflicts. Chunks land on local disk; on the last chunk the controller streams the file once into the store and deletes tus's copy.
- 1 GiB in 8 MiB chunks, the connection dropped mid-chunk at ~50 % and ~99 % (raw socket: headers for 8 MiB, half the bytes, hang-up): resumed and arrived byte-identical; the store ran once; a re-sent final chunk is refused. 8.3 s on loopback (~125 MB/s), peak heap ≤ 174 MiB with `-Xmx256m` (app, client and test in one JVM).
- tus-java-client sends chunks as POST with `X-HTTP-Method-Override: PATCH`; server code reacting to completed uploads must honour the override. Storing inside the last PATCH makes the client wait; the app should hand the stored file to a background job.

## Measurements (medians of 3 runs; 1 GiB = `large.csv`, 1,079 MB, 9.9 M rows × 20 columns)

| Step | Large object | Garage (S3), for reference |
|---|---|---|
| store original | 14.7 s | 8.1 s |
| read original back | 6.3 s | 3.6 s |
| disk for the original | 480 MB (pages compressed ~2.2×) | 199 MB (zstd ~5.4×) |

| Step | Candidate 2: `dataset_rows(dataset_id, row_no, vals text[])` | Candidate 3: `ds_<id>(row_no, c0 … cN)` |
|---|---|---|
| prepare: read the large object, parse, `COPY` | 32.5 s | 24.6 s |
| Spark JDBC `count()`, 4 partitions | 7.1 s | 4.4 s |
| Spark JDBC `dropDuplicates().count()`, 4 partitions | 58.0 s | 47.7 s |
| disk, table + index | 3.02 GB (clean run) | 1.71 GB |
| widest file | no limit found (2,000 columns tested) | 1,599 data columns (PostgreSQL's 1,600-column limit) |
| deleting a dataset | `DELETE` + `VACUUM`; the primary-key index keeps 596 MB until `REINDEX` | `DROP TABLE`, space freed at once |
| schema | fixed, created by a migration | DDL at runtime: the app's role needs `CREATE` (in a dedicated schema) |
| code (spike, non-blank lines) | 29 | 29 (plus 48 shared helpers, 14 CSV parser) |

Both candidates reproduce `small.csv` and all 18 awkward files exactly (multiset of rows, column names included): 500 columns, odd names (spaces, dots, quotes, Polish letters, 70 characters), a quoted line break, BOM + CRLF, empty cells (kept as empty strings: `COPY` CSV with every field quoted), the golden edge inputs. Peak app heap: 121–141 MiB while preparing, ~40 MiB while Spark reads.

## 4 GB extrapolation (local VM, linear in size)

- Upload: ~35 s on loopback; over a network, bandwidth decides.
- Original as a large object: ~60 s to store, ~1.9 GB disk.
- Rows: candidate 3 ~100 s to prepare, ~6.8 GB disk; candidate 2 ~130 s, ~12 GB.
- Spark: `count()` ~18 s (cand. 3) / ~28 s (cand. 2); a full-width `dropDuplicates` ~3–4 min on the 2-core / 3 GB worker, the first step of every run (de-duplication) — memory beyond 1 GiB was not measured.
- Disk per 4 GB dataset: ~9 GB (cand. 3) to ~14 GB (cand. 2) plus WAL. The first 1 GiB run filled the 98 GB Docker VM (mostly unrelated images and build cache) and crashed PostgreSQL with "No space left on device": the per-user storage limits in `PLAN.md` matter, and `max_wal_size` should be set deliberately.

## Practical notes

- `docker compose` is not wired as a plugin on this machine; the spike used the standalone `docker-compose` (5.7.0).
- The Gradle wrapper's Java downloader failed against GitHub's release CDN (503, timeouts) while `curl` worked: the 9.9.0 distribution was fetched with `curl` into `~/.gradle/wrapper/dists` and checked against the published SHA-256.
- The repo's `.gitignore` ignores `*.jar`, including `gradle/wrapper/gradle-wrapper.jar`; Phase 2 needs an exception.
- Gradle 9 fails a test task that finds no tests (`failOnNoDiscoveredTests`).
- AWS SDK v2 ≥ 2.30 sends flexible checksums that Garage rejects (`requestChecksumCalculation(WHEN_REQUIRED)`): moot with PostgreSQL only.

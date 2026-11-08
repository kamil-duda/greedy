# Phase 1: Spike — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Answer, with running code and measured numbers, the questions Phase 2 cannot start without: does the stack work together over Spark Connect, can Spark read and write PostgreSQL in parallel, can the app take resumable 4 GB-class uploads with flat memory, and which of the four upload-storage candidates to build.

**Architecture:** Everything throwaway lives under `spike/`: a Docker Compose stack (PostgreSQL, Garage as the S3-compatible store, Spark master, worker, Connect server, History Server) and a small Gradle build (a Kotlin Spring Boot app and a Scala module) running on the host and talking to the stack over `127.0.0.1`. Each task ends with an executable check (a JUnit test or a `make` target) whose output is the evidence; measurements go to one TSV file. The last task records decisions in `PLAN.md` and `docs/plans/phase-1-notes.md`, then deletes `spike/`.

**Tech Stack:** Spark 4.3.0 (`apache/spark:4.3.0-scala2.13-java21-ubuntu`, Hadoop 3.5.0, Connect server bundled), Spark Connect JVM client 4.3.0, Scala 2.13.18, Kotlin 2.3.21, Spring Boot 4.1.2, Gradle 9.8.2 wrapper with a JDK 21 toolchain, PostgreSQL 18.6 (`postgres:18.6`), pgjdbc 42.7.14, Garage v2.5.0 (`dxflrs/garage:v2.5.0`), hadoop-aws 3.5.0 + AWS SDK v2 bundle 2.35.4, tus-java-server 1.0.0-3.3, tus-java-client 0.5.1, FastCSV 4.4.0, JUnit 5.

**Spec:** `PLAN.md` — "Phase 1: Spike (throwaway)", "Target stack", "Architecture" (Spark runtime, PostgreSQL data model and its four candidates, Upload and run flow), "Security", "Risks". Versions checked 2026-11-08.

## Global Constraints

- Throwaway: all spike code, config and data under `spike/`; root `Makefile` gets `spike-*` targets only. Task 10 deletes both; `docs/plans/phase-1-notes.md` and the `PLAN.md` / `README.md` changes stay.
- Spark 4.3.0, not 4.2.x: released 2026-10-30, the newest, per the owner's rule "newest Spark supports" (`PLAN.md` is updated alongside this plan).
- No changes under `src/main/**`, `src/test/**`, `golden/**` or the `legacy-*` targets; `make legacy-test` keeps passing.
- Every published port binds `127.0.0.1`: PostgreSQL 5432, Garage S3 3900, Spark master UI 8090 (RPC 7077 stays on the Compose network), worker UI 8091, Connect gRPC 15002, driver UI 4040, History 18080, spike app 8081 (8080 belongs to the legacy app).
- Addresses are configuration, never co-location: the app reaches services at `localhost:<port>`, Spark executors at Compose names (`postgres:5432`, `garage:3900`). No code path may hand Spark an address that only the host can resolve.
- Memory budget on the 8 GB Docker VM: worker 2 cores / 3 GB, Connect driver 1 GB, PostgreSQL 1.5 GB (`shared_buffers=512MB`), Garage, master and History small. Run `make legacy-down` before measuring.
- Measurements: one row per measured step in `spike/results/measurements.tsv`, header `candidate	input	step	seconds	peak_heap_mb	storage_bytes	ok	note`; each measurement is taken 3 times and all three rows are kept.
- Long runs (large-file generation, uploads, measurements): background, log file, `caffeinate -di -w <make pid>`, check every few minutes (`AGENTS.md`).
- Git: Conventional Commits on `master-ai`, e.g. `chore(spike): …`, `docs(plan): …`; never push. Search with `rg`.

## Review Focus

1. Executors handed the app's `localhost` JDBC or S3 URL: reads hang or fail with connection refused on the worker. Expected: per-side addresses; pinned by Task 4's executor-address check.
2. A JDBC partitioned read whose `upperBound` is wrong or whose `row_no` has gaps: one huge partition or empty ones, and a slow read blamed on the candidate. Expected: bounds from `min/max(row_no)`, partition count asserted; pinned in Task 8.
3. An upload interrupted at its last chunk, or a client that re-sends the final chunk: truncated or doubled data. Expected: byte-identical file; pinned by Task 5's 99 % interruption.
4. A quoted value containing a line break: Spark's CSV reader without `multiLine` splits the record, so candidate 4 silently disagrees with the app's parser. Expected: round-trip equality or a recorded failure; pinned by the awkward set (Task 6) and Task 9.
5. More than 1,600 columns, or column names that are not valid SQL identifiers: candidate 3's per-upload table cannot be created. Expected: a recorded failure for candidate 3, success for the others; pinned by the awkward set and Task 8.

---

### Task 1: Spike stack

**Files:**
- Create: `spike/compose.yaml`, `spike/garage/garage.toml`, `spike/stack/init-garage.sh`, `spike/stack/check.sh`, `spike/.gitignore` (`results/data/`, `build/`, `.gradle/`)
- Modify: `Makefile` (`spike-up`, `spike-down`, `spike-ui`, `spike-check`)

**Interfaces:**
- Produces: running services `postgres` (db `spike`, user `spike`, password from `spike/.env`, not committed; `spike/.env.example` committed), `garage` (bucket `uploads`, access key in `spike/.env`), `spark-master`, `spark-worker`, `spark-connect`, `spark-history`; `make spike-check` exit 0 when all are ready.

- [ ] **Step 1: Write the check** `spike/stack/check.sh`: fails unless `pg_isready` succeeds on `127.0.0.1:5432`, the bucket `uploads` exists on `127.0.0.1:3900` (garage CLI in the container), HTTP 200 from 8090, 8091, 4040, 18080, and `15002` accepts TCP. Print one line per service.
- [ ] **Step 2: Run `make spike-check` before anything is up.** Expected: FAIL on the first service.
- [ ] **Step 3: Write `compose.yaml` and the Makefile targets.** All Spark services from `apache/spark:4.3.0-scala2.13-java21-ubuntu`; master and worker via `spark-class` in the foreground; Connect server via `spark-submit --class org.apache.spark.sql.connect.service.SparkConnectServer --master spark://spark-master:7077` with `spark.connect.grpc.binding.port=15002`, `spark.scheduler.mode=FAIR`, `spark.eventLog.enabled=true` to a volume shared with `spark-history`, `spark.driver.memory=1g`, `spark.executor.memory=2g`, and `--packages org.postgresql:postgresql:42.7.14,org.apache.hadoop:hadoop-aws:3.5.0,software.amazon.awssdk:bundle:2.35.4` (Ivy cache on a named volume); S3A settings for `http://garage:3900`, path-style access, credentials from `.env`. `init-garage.sh` assigns the single-node layout, creates the key and the `uploads` bucket (idempotent). `make spike-ui` prints the URLs.
- [ ] **Step 4: `make spike-up && make spike-check`.** Expected: every service OK; `docker stats --no-stream` within the memory budget.
- [ ] **Step 5: Commit** — `chore(spike): add the Compose stack for the Phase 1 spike`.

### Task 2: App and Scala module on Spark Connect

**Files:**
- Create: `spike/settings.gradle.kts`, `spike/build.gradle.kts`, `spike/gradle/libs.versions.toml`, Gradle wrapper (`gradle wrapper --gradle-version 9.8.2`), `spike/app/build.gradle.kts`, `spike/app/src/main/kotlin/spike/SpikeApplication.kt`, `spike/app/src/main/kotlin/spike/SparkConfig.kt`, `spike/app/src/main/resources/application.yml`, `spike/rules/build.gradle.kts`
- Test: `spike/app/src/test/kotlin/spike/SparkConnectSmokeIT.kt`
- Modify: `Makefile` (`spike-test` = unit + classic tests, `spike-it` = tests needing the stack)

**Interfaces:**
- Produces: Spring bean `sparkSession(): org.apache.spark.sql.SparkSession` built with `SparkSession.builder().remote(props.connectUrl)` (`spike.spark.connect-url`, default `sc://localhost:15002`); `@ConfigurationProperties("spike")` class `SpikeProperties(spark: Spark(connectUrl), db: Db(appUrl, sparkUrl, user, password), s3: S3(appEndpoint, sparkEndpoint, bucket, accessKey, secretKey))`; module `rules` compiles against `spark-sql-api_2.13:4.3.0` only.

- [ ] **Step 1: Write `SparkConnectSmokeIT`**: Spring context starts; `sparkSession.range(1000).count() == 1000L`; `sparkSession.version() == "4.3.0"`.
- [ ] **Step 2: `make spike-it`.** Expected: FAIL (no build yet / no bean).
- [ ] **Step 3: Build files, application, `SparkConfig`.** JDK 21 toolchain; Spring Boot 4.1.2, Kotlin 2.3.21, `spark-connect-client-jvm_2.13:4.3.0`; JVM test arguments the client needs on JDK 21 (expect `--add-opens=java.base/java.nio=ALL-UNNAMED`; record what was actually needed).
- [ ] **Step 4: `make spike-it`.** Expected: PASS. Record in `spike/results/notes.md`: every dependency conflict and exclusion, JVM flags, startup time.
- [ ] **Step 5: Commit** — `chore(spike): run a Spring Boot app on Spark Connect 4.3`.

### Task 3: One Scala API, two sessions

**Files:**
- Create: `spike/rules/src/main/scala/spike/rules/RoundProbe.scala`
- Test: `spike/rules/src/test/scala/spike/rules/RoundProbeSpec.scala`, run by two Gradle test suites: `testClassic` (runtime `spark-sql_2.13:4.3.0`, `master("local[2]")`) and `testConnect` (runtime `spark-connect-client-jvm_2.13:4.3.0`, `remote("sc://localhost:15002")`)

**Interfaces:**
- Produces: `object RoundProbe { def bestPerGroup(df: DataFrame): DataFrame; def counted(df: DataFrame, checkpointDir: String): DataFrame }` — `bestPerGroup` ranks rows per `group` by `score` desc then `col_index` asc with a window and keeps rank 1; `counted` caches, local-checkpoints and counts per `group`.

- [ ] **Step 1: Write `RoundProbeSpec`** (ScalaTest or JUnit 5 from Scala, either): for a 6-row table with a score tie broken by `col_index`, `bestPerGroup` returns exactly the expected 2 rows; `counted` returns the expected counts; the test collects only the final result.
- [ ] **Step 2: Run `testClassic` and `testConnect`.** Expected: FAIL (no implementation).
- [ ] **Step 3: Implement `RoundProbe`** using only the shared API (no RDDs, no `SparkContext`, no UDFs).
- [ ] **Step 4: Run both suites.** Expected: both PASS with identical results. If a feature works only on one side (checkpoint over Connect in particular), record it in `notes.md` with the error.
- [ ] **Step 5: Commit** — `chore(spike): run the same Scala code on classic and Connect sessions`.

### Task 4: PostgreSQL over JDBC from a Spring bean; run tags; cancel

**Files:**
- Create: `spike/app/src/main/kotlin/spike/jdbc/JdbcProbe.kt`
- Test: `spike/app/src/test/kotlin/spike/jdbc/JdbcProbeIT.kt`, `spike/app/src/test/kotlin/spike/jdbc/CancelByTagIT.kt`

**Interfaces:**
- Consumes: `sparkSession`, `SpikeProperties` (Task 2).
- Produces: `JdbcProbe.loadRows(table: String, rows: Long): Long` (app side: create table `row_no bigint primary key, a text, b text, d text`, load with pgjdbc `CopyManager`); `JdbcProbe.roundTrip(runId: String, source: String, target: String, partitions: Int): Long` (Spark side: `addTag("run-$runId")`, read with `partitionColumn=row_no`, bounds from `min/max`, `numPartitions`, group by `d`, write `target` over JDBC with `reWriteBatchedInserts=true`; returns rows written).

- [ ] **Step 1: Write `JdbcProbeIT`**: `loadRows("probe_rows", 1_000_000) == 1_000_000`; `roundTrip("t1", "probe_rows", "probe_out", 4)` writes one row per distinct `d`; the driver REST API (`http://localhost:4040/api/v1/applications/<id>/jobs`) lists jobs whose tags include `run-t1`; the read stage ran 4 tasks; Spark's JDBC URL in the plan is `jdbc:postgresql://postgres:5432/spike`, never `localhost` (Review Focus 1). Append load and round-trip timings to `measurements.tsv` (`candidate=probe`).
- [ ] **Step 2: Write `CancelByTagIT`**: start a job taking > 60 s under tag `run-cancel` on another thread; `sparkSession.interruptTag("run-cancel")` after 3 s; the job fails with an interruption within 10 s and no job with that tag is still running.
- [ ] **Step 3: `make spike-it`.** Expected: both FAIL (no `JdbcProbe`).
- [ ] **Step 4: Implement `JdbcProbe`.**
- [ ] **Step 5: `make spike-it`.** Expected: PASS; tagged jobs visible in the driver UI and, after the run, in the History Server.
- [ ] **Step 6: Commit** — `chore(spike): read and write PostgreSQL over JDBC from Spark Connect`.

### Task 5: Resumable upload with flat memory

**Files:**
- Create: `spike/app/src/main/kotlin/spike/upload/TusUploadController.kt`, `spike/app/src/main/kotlin/spike/upload/UploadSink.kt`
- Test: `spike/app/src/test/kotlin/spike/upload/ResumableUploadIT.kt`

**Interfaces:**
- Produces: endpoint `/uploads` (tus 1.0 via tus-java-server 1.0.0-3.3, chunks on local disk under `spike/results/data/tus`); `interface UploadSink { fun store(uploadId: String, input: java.io.InputStream): StoredOriginal }`, `data class StoredOriginal(val kind: String, val ref: String, val bytes: Long, val sha256: String)` — called once when an upload completes; Tasks 7–9 provide implementations.

- [ ] **Step 1: Write `ResumableUploadIT`** with tus-java-client 0.5.1 against the app started with `-Xmx256m`: a 1 GiB file (from Task 6, or generated in the test if Task 6 is not done) is uploaded in 8 MiB chunks; the client connection is dropped at ~50 % and again at ~99 % (Review Focus 3) and resumed; the completed upload's SHA-256 equals the source's; the app never runs out of memory; peak heap and total time go to `measurements.tsv` (`candidate=upload`).
- [ ] **Step 2: Run it.** Expected: FAIL (no endpoint).
- [ ] **Step 3: Implement the controller** with tus-java-server; on completion stream the file to the configured `UploadSink` (a no-op sink that only hashes, until Task 7).
- [ ] **Step 4: Run it.** Expected: PASS within the heap limit. Record chunk size, throughput and any tus/Spring Boot 4 friction in `notes.md`.
- [ ] **Step 5: Commit** — `chore(spike): resumable uploads with tus and flat memory`.

### Task 6: Test inputs

**Files:**
- Create: `spike/data/generate.py` (Python 3 stdlib, run with `python3 -I`), output under `spike/results/data/` (git-ignored)
- Test: `spike/data/check_inputs.py`

**Interfaces:**
- Produces, with a fixed seed: `small.csv` (copy of `golden/inputs/public/car.csv`); `large.csv` (20 columns `c1..c19,d`, values from 2–6 letter alphabets, ~1 GiB, about 10 M rows); awkward set `awkward/`: `wide-500.csv` (500 columns × 1,000 rows), `wide-2000.csv` (2,000 columns × 100 rows), `names.csv` (column names with spaces, dots, quotes, Polish letters, a 70-character name), `quoted-newline.csv` (a quoted value containing a line break, Review Focus 4), `bom-crlf.csv`, `empty-cells.csv`, plus copies of every `golden/inputs/edge/*.csv` except `empty-file.csv` and `duplicate-names.csv` (rejected at upload by registry #11/#15).

- [ ] **Step 1: Write `check_inputs.py`**: every expected file exists; `large.csv` is between 0.95 and 1.1 GiB and has 20 columns; `wide-2000.csv` has 2,000 columns; `quoted-newline.csv` parses with Python's `csv` module into the expected number of records.
- [ ] **Step 2: Run it.** Expected: FAIL (nothing generated).
- [ ] **Step 3: Write `generate.py`.**
- [ ] **Step 4: Generate (background, caffeinate) and run the check.** Expected: PASS; generation time in `notes.md`.
- [ ] **Step 5: Commit** — `chore(spike): generate the inputs for the storage comparison`.

### Task 7: Original storage: large object vs object store (candidate 1)

**Files:**
- Create: `spike/app/src/main/kotlin/spike/store/LargeObjectSink.kt`, `spike/app/src/main/kotlin/spike/store/ObjectStoreSink.kt`
- Test: `spike/app/src/test/kotlin/spike/store/OriginalStorageIT.kt`

**Interfaces:**
- Consumes: `UploadSink`, `StoredOriginal` (Task 5); inputs (Task 6).
- Produces: `LargeObjectSink` (`kind="lo"`, `ref`=OID, streams through pgjdbc `LargeObjectManager` in one transaction) and `ObjectStoreSink` (`kind="s3"`, `ref`=object key in `uploads`, AWS SDK v2 multipart upload with 8 MiB parts); both expose `open(ref): InputStream` for reading back.

- [ ] **Step 1: Write `OriginalStorageIT`**: for each sink and each of `small.csv`, `large.csv`: store, read back, SHA-256 equal; app heap stays under 256 MiB; measure store time, read-back time and storage (`pg_largeobject` growth / bucket object size) into `measurements.tsv` (`candidate=original-lo|original-s3`), 3 runs each.
- [ ] **Step 2: Run it.** Expected: FAIL.
- [ ] **Step 3: Implement both sinks.**
- [ ] **Step 4: Run it (background for `large.csv`).** Expected: PASS. Note for candidate 1 in `notes.md`: Spark cannot read a large object in parallel; the cost of making it readable equals candidate 2's or 3's preparation, so candidate 1 is evaluated as "original storage only".
- [ ] **Step 5: Commit** — `chore(spike): measure original storage in large objects and Garage`.

### Task 8: Rows in PostgreSQL (candidates 2 and 3)

**Files:**
- Create: `spike/app/src/main/kotlin/spike/prepare/CsvRows.kt`, `spike/app/src/main/kotlin/spike/prepare/SharedTablePreparer.kt`, `spike/app/src/main/kotlin/spike/prepare/TablePerUploadPreparer.kt`
- Test: `spike/app/src/test/kotlin/spike/prepare/RowCandidatesIT.kt`

**Interfaces:**
- Consumes: `LargeObjectSink.open` (Task 7), `sparkSession`, `SpikeProperties`.
- Produces: `CsvRows.stream(input: InputStream): Sequence<List<String>>` (FastCSV 4.4.0, RFC 4180, BOM dropped, header first); `SharedTablePreparer.prepare(datasetId: Long, input: InputStream): Long` (`dataset_rows(dataset_id bigint, row_no bigint, vals text[], primary key (dataset_id, row_no))`, `COPY`); `TablePerUploadPreparer.prepare(datasetId: Long, input: InputStream): Long` (`ds_<id>(row_no bigint primary key, c0 text, … cN text)`, `COPY`); `fun sparkRead(datasetId: Long, columns: List<String>, partitions: Int): DataFrame` on each, returning a DataFrame with the original column names.

- [ ] **Step 1: Write `RowCandidatesIT`**: for both preparers, `small.csv` and every awkward file: prepare, then the rows Spark reads (collected only in the test) equal `CsvRows.stream` of the original, in order of `row_no`, with the original column names; `wide-2000.csv` and non-identifier names: candidate 2 PASS, candidate 3 expected to fail at table creation — the test records the failure instead of failing (Review Focus 5); Spark's read uses bounds from `min/max(row_no)` and the stage has exactly `partitions` tasks (Review Focus 2). Then for `large.csv`: preparation time, Spark read time (`count()` and `dropDuplicates().count()`), table size (`pg_total_relation_size`), peak heap → `measurements.tsv` (`candidate=rows-shared|rows-table`), 3 runs.
- [ ] **Step 2: Run it.** Expected: FAIL.
- [ ] **Step 3: Implement `CsvRows` and both preparers.**
- [ ] **Step 4: Run it (background for `large.csv`).** Expected: PASS with candidate 3's recorded failures.
- [ ] **Step 5: Commit** — `chore(spike): measure rows in PostgreSQL, shared table vs table per upload`.

### Task 9: Spark reads the object store (candidate 4)

**Files:**
- Create: `spike/app/src/main/kotlin/spike/prepare/ObjectStoreReader.kt`
- Test: `spike/app/src/test/kotlin/spike/prepare/ObjectStoreCandidateIT.kt`

**Interfaces:**
- Consumes: `ObjectStoreSink` (Task 7), `CsvRows` (Task 8), `sparkSession`.
- Produces: `ObjectStoreReader.validate(ref: String): Long` (app side: streams the object through `CsvRows`, returns row count, never stores rows); `ObjectStoreReader.sparkRead(ref: String): DataFrame` (Spark's CSV reader on `s3a://uploads/<ref>`, header on, every column a string, options chosen to match `CsvRows`: encoding UTF-8, quote and escape `"`, `multiLine` as needed).

- [ ] **Step 1: Write `ObjectStoreCandidateIT`**: for `small.csv` and every awkward file, the rows Spark reads equal `CsvRows.stream` of the same object, including `quoted-newline.csv` (Review Focus 4) and `bom-crlf.csv` (no BOM in the first column name, registry #13); record the Spark options that achieve it, and whether `multiLine` costs parallelism on `large.csv`. Then `large.csv`: validation time, Spark read time (`count()`, `dropDuplicates().count()`), peak heap → `measurements.tsv` (`candidate=s3`), 3 runs.
- [ ] **Step 2: Run it.** Expected: FAIL.
- [ ] **Step 3: Implement `ObjectStoreReader`.**
- [ ] **Step 4: Run it (background for `large.csv`).** Expected: PASS, or every disagreement recorded with the input and the Spark options tried.
- [ ] **Step 5: Commit** — `chore(spike): measure Spark reading uploads from the object store`.

### Task 10: Decisions, notes and clean-up

**Files:**
- Create: `docs/plans/phase-1-notes.md`
- Modify: `PLAN.md` (Phase 1 checkboxes, decisions, data model, target stack), `README.md` (versions table), `Makefile` (remove `spike-*`)
- Delete: `spike/`

**Interfaces:**
- Consumes: `spike/results/measurements.tsv`, `spike/results/notes.md`.

- [ ] **Step 1: Summarize** in `docs/plans/phase-1-notes.md`: per candidate the median of the three runs for every step, storage used, peak heap, awkward-set results, failures; Connect findings (flags, conflicts, classic vs Connect differences, tags, cancel); upload findings; the 4 GB extrapolation from measured throughput on the local VM; per candidate the lines of code it took, what deleting a dataset involves, and what backing it up would take; and for candidate 4 that RSES `.tab` uploads (Phase 7) would need a converted CSV copy. Copy `measurements.tsv` to `docs/plans/phase-1-data/measurements.tsv`.
- [ ] **Step 2: Recommend and ask.** Present the owner the recommended upload storage (and original storage), the Spark Connect decision (or the `spark-submit` fallback), and the upload protocol, with the numbers; wait for the owner's choice.
- [ ] **Step 3: Record the decisions in `PLAN.md`**: tick the Phase 1 boxes, replace "decided in the Phase 1 spike" in the data model and target stack with the decision, adjust Phases 2–4 items it changes, list any new risks; update `README.md`'s versions table.
- [ ] **Step 4: Delete `spike/` and the `spike-*` targets; `make legacy-test` still passes; `make spike-down` was run first and `docker volume ls` shows no `spike_` volumes.**
- [ ] **Step 5: Commit** — `docs(plan): record the Phase 1 spike decisions` and `chore(spike): remove the Phase 1 spike code`.

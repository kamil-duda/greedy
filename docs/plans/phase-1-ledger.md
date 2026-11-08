Execution ledger of Phase 1, copied verbatim on 2026-11-08 from the local superpowers workspace (`.superpowers/sdd/phase-1/progress.md`, since deleted). Every ruling made during the spike and its final review, with evidence and cost if wrong.

# SDD ledger — plan: docs/plans/phase-1.md
Spec: PLAN.md (Phase 1: Spike; Target stack; Architecture incl. PostgreSQL data model and the four candidates; Upload and run flow; Security; Risks)

## Standing rulings
Ruling: work on master-ai in the main working tree, no git worktree — AGENTS.md: agents commit only on master-ai, and `git worktree` asks the owner — cost if wrong: none; the spike lives under spike/.
Ruling: no harness todo list (no todo tool in this session); the ledger is the record — cost if wrong: none.

## Pre-flight (shared interfaces)
| Producer → Consumer | Produces | Consumes | Finding |
|---|---|---|---|
| T1 → T2–T9 | stack services, `.env`, ports | app config (localhost ports), Spark config (Compose names) | consistent |
| T2 → T3, T4, T5, T7, T8, T9 | `sparkSession`, `SpikeProperties(spark, db(appUrl, sparkUrl,…), s3(appEndpoint, sparkEndpoint,…))`, module `rules` | same names | consistent |
| T5 → T7 | `UploadSink.store(uploadId, InputStream): StoredOriginal(kind, ref, bytes, sha256)` | sinks implement `UploadSink`, add `open(ref)` | consistent |
| T6 → T5, T7, T8, T9 | `spike/results/data/{small,large}.csv`, `awkward/*` | 1 GiB file in T5; all files in T7–T9 | ORDER CONFLICT: T5 needs T6's large.csv |
| T7 → T8, T9 | `LargeObjectSink.open`, `ObjectStoreSink` | T8 reads originals from LO; T9 from S3 | consistent |
| T8 → T9 | `CsvRows.stream(InputStream): Sequence<List<String>>` | T9 compares Spark rows with `CsvRows` | consistent |
Pre-flight: Ruling: run Task 6 before Task 5 — T5's test uses T6's 1 GiB file (the plan allows generating it in the test, but one generator is simpler and keeps inputs identical across tasks) — cost if wrong: none; order change only.
Task 1: Ruling: use the standalone docker-compose (v5.7.0) — 'docker compose' is not wired as a CLI plugin on this machine; changing the owner's Docker config is out of scope — cost if wrong: none.
Task 1: complete (commits df9f92d..e9b45ef, tests: make spike-check → OK    spark-history UI http://127.0.0.1:18080)
Ruling: Task 6 (inputs, Python only) is worked while Task 2 waits for the Gradle distribution download (GitHub answering 503) — order change only; Task 6 consumes nothing from Tasks 2–5 — cost if wrong: none.
Task 6: Ruling: golden edge copies are named edge-<name>.csv (bom-crlf and empty-cells would collide with the generated files) and header-only.csv is excluded too (rejected at upload by #15, like empty-file) — cost if wrong: none.
Task 6: complete (commits e9b45ef..69a37a5, tests: python3 -I spike/data/check_inputs.py spike/results/data → inputs ok: 20 files)
Task 2: note: the Gradle wrapper's Java downloader kept failing against GitHub's release CDN (503/timeouts) while curl succeeded; the 9.9.0 zip was fetched with curl into ~/.gradle/wrapper/dists and verified against the published SHA-256 (0b00b0ff…177a). networkTimeout raised to 120 s in gradle-wrapper.properties.
Task 2: Ruling: excluded only log4j-slf4j2-impl from the Spark client (the plan listed no exclusions; log4j-core is needed at runtime) and kept --add-opens=java.base/java.nio=ALL-UNNAMED (verified necessary) — cost if wrong: none; both are findings for Phase 2.
Task 2: complete (commits e9b45ef..a13c8ab, tests: make spike-it → Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.9.0/userguide/configuration_cache_enabling.html)
Task 3: Ruling: counted(df) has no checkpointDir parameter (localCheckpoint needs none); reliable checkpoint probed separately via reliablyCheckpointed + a recording test, then made to work by configuring spark.checkpoint.dir (classic builder; Connect server on s3a://checkpoints) — cost if wrong: none; findings for Phase 3.
Task 3: complete (commits a13c8ab..f30c1bd, tests: bash -c 'make spike-test && make spike-it' → Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.9.0/userguide/configuration_cache_enabling.html)
Task 4: Ruling: roundTrip takes an optional url (default the Spark-side URL) so the test can prove Review Focus 1 with a real localhost round trip that must fail; bounds are read on the app side with plain JDBC — cost if wrong: none.
Task 4: complete (commits f30c1bd..906c4d8, tests: make spike-it → Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.9.0/userguide/configuration_cache_enabling.html)
Task 4: note: every run of the measuring ITs appends rows to measurements.tsv; Task 10 uses the latest three runs per (candidate, input, step) and ignores rows marked false.
Task 5: Ruling: drops are simulated with a raw socket that announces an 8 MiB chunk, sends half and hangs up (the tus client cannot be cut mid-request); the test JVM is capped at 256 MiB for all ITs (maxHeapSize), which also caps the app — cost if wrong: none.
Task 5: complete (commits 906c4d8..2ac6fb2, tests: bash -c 'cd spike && ./gradlew --no-daemon -q integrationTest --tests "spike.upload.*" --rerun' → 2026-11-08T13:14:35.512+01:00  INFO 71604 --- [tomcat-shutdown] o.s.boot.tomcat.GracefulShutdown         : Graceful shutdown complete)
Task 7: Ruling: UploadSink gains `val kind` and the tus controller picks the sink named by spike.upload.sink (the plan's single-sink injection breaks once three sinks are beans) — cost if wrong: none.
Task 7: Ruling: storage_bytes records physical disk growth (pg_total_relation_size of pg_largeobject; Garage's data volume via a helper container) with the logical size in the note — the plan named "pg_largeobject growth / bucket object size" but compression makes the two differ ~2-5x — cost if wrong: none; both are recorded.
Task 7: complete (commits 2ac6fb2..37ff8a0, tests: bash -c 'cd spike && ./gradlew --no-daemon -q integrationTest --tests "spike.store.*" --rerun' → 2026-11-08T13:24:29.901+01:00  INFO 73816 --- [ionShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown completed.)
Ruling (owner, 2026-11-08): PostgreSQL only — originals as large objects, Spark reads rows from PostgreSQL over JDBC; no object store in the target. Reasons (owner): one storage system means less infrastructure and code (no second service to run, secure, back up and keep consistent), accepted although Garage measured ~2x faster and ~2.4x smaller for originals. Task 9 (candidate 4) is dropped; Garage stays in the throwaway stack until Task 10 deletes spike/ (Task 8 runs on it; the Connect server's reliable checkpoints use it) — cost if wrong: candidate 4 has no measured numbers; the Task 7 originals numbers stand in.
Task 8: note: first run crashed PostgreSQL with 'No space left on device' — the Colima VM disk (98 GB) was full, mostly with the owner's CI runner images and 14.6 GB build cache; the spike's pgdata volume had grown to 7.9 GB (1 GiB as rows + large object + up to 4 GB WAL). Reset the spike's own pgdata (throwaway), max_wal_size 4GB -> 1GB, reran.
Task 8: Ruling: Spark-read rows are compared with the app's parse as a multiset (sorted), not "in order of row_no" — sparkRead returns only the original columns, and order carries no meaning for the algorithm; duplicates still count — cost if wrong: a reordering bug in reading would go unnoticed (none plausible: rows are compared whole).
Task 8: Ruling: candidate 3 stores columns under positional names (c0…), so the plan's expected candidate-3 failure on non-identifier names does not occur; its only failure is >1,599 columns — cost if wrong: none.
Task 8: Ruling: task-done reruns the three quick tests; the 10-minute 1 GiB measurement passed in this session's final run (t8-green.log) and is not repeated — cost if wrong: none.
Task 8: complete (commits 37ff8a0..ce62365, tests: bash -c 'cd spike && ./gradlew --no-daemon -q integrationTest --tests "spike.prepare.RowCandidatesIT.Spark*" --tests "spike.prepare.RowCandidatesIT.the*" --tests "spike.prepare.RowCandidatesIT.a file*" --rerun' → 2026-11-08T13:43:38.209+01:00  INFO 78854 --- [ionShutdownHook] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Shutdown completed.)
Task 9: dropped by the owner (PostgreSQL only, 2026-11-08); no candidate-4 code was written.
Ruling (owner, 2026-11-08): rows in one shared table, dataset_rows(dataset_id, row_no, vals text[]) (candidate 2), not a table per upload — tables per upload would snowball, and PostgreSQL limits a table to 1,600 columns. Accepted: ~25-60% slower prepare/reads and ~1.8x disk vs candidate 3, periodic index maintenance.
Task 10: Ruling: committed the spike removal before the decisions (the plan listed them the other way round) so the ticked 'Spike code deleted' box is true in the commit that ticks it; task-done verifies the legacy-suite log produced in this task plus the absence of spike/ and its volumes instead of rerunning the 5-minute suite — cost if wrong: none.
Task 10: complete (commits ce62365..aa9ff86, tests: bash -c 'rg -q "BUILD SUCCESS" .superpowers/sdd/phase-1/t10-legacy.log && rg -q "^exit=0" .superpowers/sdd/phase-1/t10-legacy.log && [ ! -e spike ] && ! docker volume ls --format "{{.Name}}" | rg -q "^greedy-spike_" && ! rg -q spike Makefile && echo "legacy suite green; spike, its volumes and targets gone"' → legacy suite green; spike, its volumes and targets gone)
Final: review by a fresh reviewer (opus) on df9f92d..aa9ff86: 0 Critical, 9 Important, 12 Minor; verdict "With fixes"; all fixes are documentation (the spike code is deleted).
Final: re-grade: M1 (wrong figures: 24.6 vs 24.5 s, "all 18" files for candidate 3, Garage disk from run 1 under a "medians" header, "2x" vs ~1.8x) and M2 (heap method unstated, 247-250 MiB peaks omitted) → Important: they misstate the record the owner decides from. M12's per-user scoping of tus uploads → Important: without it one user can probe another's upload by id (anti-goal). M7, M9, M10 are folded into the fixes that rewrite the same lines (I8, I7, I1/I3).
Final: Ruling: documentation fixes have no failing test to write; each is verified by recomputing the figure from docs/plans/phase-1-data/measurements.tsv or by an rg consistency check over PLAN.md — cost if wrong: none.
Final: Ruling: dataset_rows gets no owner_id column; the exception is documented — its rows are reachable only through their dataset, whose ownership the app checks before any read or run, and Spark reads by a dataset id the app hands it after that check; Spark connects with its own role limited to reading dataset_rows and writing rules — cost if wrong: one column (8 bytes/row) and a backfill later.
Final: minor (deferred): M3 candidate-2 reads measured with one live dataset in the table; Phase 4 should ANALYZE after COPY and confirm primary-key range scans per partition.
Final: minor (deferred): M4 the localhost test fails at the Connect server during schema resolution; the Spark-side URL must resolve from the Connect server and every executor.
Final: minor (deferred): M5 cancel after an app restart is untested; tags are per thread and session (store the Connect session id and reattach, or interruptAll on a per-run session).
Final: minor (deferred): M6 the History Server only showed the running Connect app; one long-running application with an ever-growing event log needs rolling event logs.
Final: minor (deferred): M8 registry #14 could also register quoted line breaks (new parser keeps them; legacy Spark 2.1 splits the record) — needs owner approval.
Final: minor (deferred): M11 Phase 2 items only in the notes: gradle-wrapper.jar .gitignore exception, standalone docker-compose, Spark's full --add-opens list for classic test sessions, the app's --add-opens in bootRun and the image, baking the JDBC driver into the Connect image instead of --packages.
Final: minor (deferred): M12 (rest) tus expiry and periodic cleanup(), the tus directory on a persistent volume, transient double disk (chunks + large object), the browser client (tus-js-client, chunkSize) in the stack tables.
Final: fixed I1 index maintenance overstated — PLAN data model/Phase 4 and notes now say deleted space is reused (3.019 → 3.331 → 3.331 GB), reclaim optional; verified against measurements.tsv and rg (no "scheduled"/"596 MB" left).
Final: fixed I2 superuser-only verification — notes state it; PLAN: autovacuum, MAINTAIN, Phase 4 tests as the app role.
Final: fixed I3 dataset_rows owner_id — documented exception in the data model (ruling above).
Final: fixed I4 fetchsize — required on every JDBC read (PLAN Spark runtime, reading, risks), finding added to the notes, Phase 4 cancel-during-scan test.
Final: fixed I5 JDBC write throughput — "grouped into 3 rows", throughput listed as unmeasured.
Final: fixed I6 tus once-only hand-over — PLAN requires the completing request + conditional status change (new STORING status) and owner scoping; notes say what was not tested; Phase 4 tests.
Final: fixed I7 Spark Connect overclaim — "everything tested", untested items listed, contingency kept; marked (proposed).
Final: fixed I8 text[] encoding — spelled out with strict UTF-8, BOM, row-number errors; Phase 4 test values.
Final: fixed I9 disk ratio — ~3.2x the CSV (3.24 recomputed), data-dependent 3-5x.
Final: fixed M1 figures (24.5 s, 17 of 18 files for candidate 3, Garage first-run disk, ~1.8x), M2 heap method and 247-250 MiB peaks, M12 per-user tus scoping, M9 markers, M10 cascade/`?`/duplicate reference — all recomputed or checked with rg.

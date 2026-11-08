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

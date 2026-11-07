# Modernization Plan

Tracking document for modernizing this project. Tick boxes as work lands. Decisions marked **(owner)** were made by the project owner; decisions marked **(proposed)** are the planner's defaults and can be overridden.

## Intent

The project is a 2017 Spring Boot app that induces decision rules with five greedy heuristics (M, RM, maxCov, poly, log). It no longer builds on a current JDK, and it uses Spark badly: every heuristic calls `collect()` and computes rules row by row on the driver, and one run on a 3-row file takes about 23 s (17 Spark jobs × 200 shuffle partitions, all inside the HTTP request). It also has no security. The goal is to bring it onto a current stack, make Spark do the whole calculation, make it safe for several users, and do all of that without silently changing what the algorithm produces.

### Goals
- Current toolchain: Gradle (Kotlin DSL), Kotlin for the app, Scala 2.13 for Spark, latest Spark. **(owner)**
- The rule-induction algorithm runs entirely as Spark transformations; no data rows are collected to the driver. **(owner)**
- Spark runs as its own local service (cluster manager, worker, dashboards) that the app connects to, so all running work can be watched in Spark's own UIs. **(owner)**
- A test safety net covering the current behavior exists **before** any algorithm or logic change, and every later change is checked against it. **(owner)**
- High test coverage of business logic and of the Spark algorithms, enforced by the build. **(owner)**
- MongoDB stays as the database, with a modern way of storing files and data. **(owner)**
- Several users share one installation; each user sees and manages only their own data. **(owner)**
- Accounts are created by an admin; there is no self sign-up. **(owner)**
- Security is part of the rewrite: login, per-user authorization on every route and query, no open admin or diagnostic endpoints. **(owner)**
- A modern server-rendered UI (Option B below). **(owner)**
- A user-friendly UI: for users, this is an app where they upload data and get rules back. Spark, jobs, MongoDB and other internals are invisible to them. **(owner)**
- Scores are compared with exact math; legacy rounding artifacts are not reproduced. **(owner)**
- All common tasks run through a `Makefile` wrapping Gradle. **(owner)**

### Non-goals
- GitHub Actions or any other CI. **(owner)**
- Dropping Spark or replacing it with an in-memory implementation. **(owner)**
- A single-page app (React/Vue/etc.) or Vaadin. **(owner)**
- A remote or production Spark cluster: Spark runs on the same machine as the app. **(owner)**
- Migrating data from a legacy deployment: there is none; the new app starts empty. **(owner)**

### Anti-goals (would make this a failure even if it works)
- Any difference between old and new rule output that is not listed in the [behavior-change registry](#behavior-change-registry).
- Driver-side collection of data in the algorithm module.
- Algorithm or logic code changed before Phase 0 is finished and reviewed.
- A user seeing, downloading, changing or even learning of the existence of another user's data.
- The UI exposing implementation details (Spark, jobs, stages, collections, stack traces) or requiring users to understand them.

## Target stack

Versions checked on 2026-10-27; pin exact patch versions in the version catalog when Phase 2 starts. The README has the full legacy-vs-current version table.

| Component | Version | Notes |
|---|---|---|
| Apache Spark | 4.2.x (4.2.1 released 2026-10-27) | **(owner)** "newest Spark supports". 4.2 line supported until Jan 2028. |
| Spark runtime | Official `apache/spark` 4.2.x image (Scala 2.13, Java 21) via Docker Compose | **(owner)** separate local Spark; service layout below **(proposed)**. |
| Spark Connect client | 4.2.x, Scala 2.13 | The app's only link to Spark **(proposed)**. |
| Scala | 2.13 (the patch version Spark 4.2 is built with) | Spark 4 supports only 2.13. **(owner)** Spark code stays in Scala. |
| JDK | 21 | **(proposed)** Spark 4.2 supports 17/21/25 (25 only from 25.0.3); 21 is installed locally. |
| Spring Boot | 4.1.x (4.1.2 current) | Includes Spring Security. |
| Kotlin | 2.3.x | Version Spring Boot 4.1 targets. |
| Gradle | 9.x, Kotlin DSL, wrapper, version catalog | **(owner)** |
| MongoDB server | current LTS, pinned (not `latest`), authentication on | |
| MongoDB Spark Connector | 11.x, `mongo-spark-connector_2.13` | Supports Spark 4.0+. Installed on the Spark side, not in the app. |
| Templates / UI | Thymeleaf + htmx 2 + Alpine.js + Tailwind CSS 4 + daisyUI 5, Tabulator, Apache ECharts | **(owner)** Option B; library picks **(proposed)**. |

## Architecture

### Modules
- `spark-rules` (Scala 2.13): data preparation, the rule-induction engine, the five heuristics, run statistics. No Spring. Uses only Spark's shared DataFrame / Column API (no RDDs, no `SparkContext`, no Scala UDFs unless unavoidable) so the same code runs over Spark Connect in the app and on an in-process session in tests. MongoDB is read and written through the connector's data source by name, so the connector is not a compile dependency. Public API is Java-friendly: takes a `SparkSession` and parameters, returns DataFrames; no Scala collections in signatures. **(proposed)**
- `app` (Kotlin, Spring Boot): web UI, security, upload ingestion, MongoDB access, run queue, exports. Depends on the Spark Connect client, not on Spark itself.
- `legacy/`: the current Maven project, moved here in Phase 2 and kept runnable (JDK 8 in Docker) to regenerate golden outputs. Deleted in Phase 8. **(proposed)**

### Spark runtime
Spark runs as its own services on the same machine, independent of the app **(owner)**. Layout **(proposed)**, all in Docker Compose, ports bound to `127.0.0.1` only:

| Service | What it is | Local URL |
|---|---|---|
| `spark-master` | Standalone cluster manager | UI http://localhost:8090 |
| `spark-worker` | Executor host (1 by default; cores and memory configurable) | UI http://localhost:8091 |
| `spark-connect` | Spark Connect server, a long-running Spark application on the master with the MongoDB Spark Connector on its classpath; event logging on | gRPC `sc://localhost:15002`, driver UI (Jobs, Stages, SQL, Connect tabs) http://localhost:4040 |
| `spark-history` | History Server reading event logs from a shared volume, so finished work stays inspectable after restarts | UI http://localhost:18080 |

The app connects through Spark Connect and is never a Spark driver itself **(proposed)**:
- Spark's dependencies stay out of the Spring Boot classpath (thin client).
- One Spark Connect session per run; every operation is tagged with the run id, so each run's jobs, stages and SQL plans are identifiable in the UI, and a run can be cancelled by interrupting its tag.
- The Connect server uses the FAIR scheduler so concurrent runs share executors.
- Data never passes through the app: executors read rows from MongoDB and write rules to MongoDB through the connector.

Fallback if the Phase 1 spike finds Spark Connect cannot do something `spark-rules` needs: each run is submitted as its own Spark application to the same standalone master (`spark-submit` / `SparkLauncher` with a `spark-rules` main class). It is equally visible in the master UI and the History Server.

Exact UI ports may move if they clash; `make spark-ui` prints them.

### MongoDB data model (proposed)
- `users`: `{_id, username, passwordHash, roles (ADMIN|USER), mustChangePassword, disabled, failedLogins, lockedUntil, createdAt, createdBy}`.
- GridFS bucket `uploads`: the original uploaded file, unchanged, accessed through the current `GridFsTemplate` / `GridFSBucket` API, `metadata.ownerId`. Used only for "download original".
- `datasets`: `{_id, ownerId, name, originalFileId, sourceFormat (csv|tab), columns: [{name, field, type}], rowCount, uploadedAt}`. `field` is a positional field name (`c0`, `c1`, …) because column names may contain `.` or `$`, which MongoDB field names cannot safely hold.
- One collection per dataset for its rows (e.g. `dataset_<id>_rows`), one document per row, values stored as strings under the positional fields. Spark reads it through the connector with an explicit schema built from `datasets.columns`. Deleting a dataset drops the collection.
- `runs`: `{_id, ownerId, datasetId, heuristic, mode (dt|is), status (QUEUED|RUNNING|SUCCEEDED|FAILED|CANCELLED), progress, error, createdAt, startedAt, finishedAt, stats: [{decisionAttribute, ruleCount, coverage {min,avg,max}, length {min,avg,max}}]}`.
- `rules`: `{runId, ownerId, decisionAttribute, conditions: [{attribute, value}], decision, support, length}`, written by Spark through the connector. Indexed on `ownerId` + `runId` (+ sort fields used by the UI).
- Every document and file carries `ownerId`; repository methods take the current user and always filter by it, so an unscoped query cannot be written by accident.

### Upload and run flow
1. Upload: the app stores the original in GridFS, stream-parses it (CSV now, RSES `.tab` in Phase 7) and bulk-inserts rows into the dataset's collection, then writes the `datasets` document. Parsing is streaming; the file is never fully loaded into memory.
2. Run: the user picks heuristic and mode; the app creates a `runs` document and queues it. Output format is **not** chosen here any more (see registry).
3. Execution: the app opens a Spark Connect session tagged with the run id and calls `spark-rules`, which reads the rows collection via the connector, prepares decision tables, induces rules, writes them to `rules`, and computes stats with a Spark aggregation (a single result row, read on the driver, which is allowed). The app stores the stats in `runs`.
4. Queue **(proposed)**: global concurrency 2 by default; fair round-robin across users; a per-user cap on queued runs; users can cancel their own runs. Progress is reported per induction round. On app restart, runs left in `RUNNING` are interrupted on the Spark side and marked `FAILED`.

### Downloads
Formats are generated on request from the `rules` collection, only for the run's owner: the app queries a run's rules in a fixed order and streams them through a formatter into the HTTP response (`Content-Disposition: attachment` with an RFC 6266-encoded file name, correct `Content-Type`). Memory use is constant regardless of rule count. RSES header data (attributes, decision values, rule count) comes from `datasets` and `runs`. Formats: legacy CSV, legacy CSV with metadata, legacy RSES (rule lines only) until Phase 7, then full RSES `.rul`; in `is` mode RSES export is a `.zip` with one `.rul` per decision attribute. Caching generated files in GridFS is not planned; add it only if exports prove slow.

### Security
Required by the owner; mechanisms **(proposed)**:
- Spring Security: everything except the login page and static assets requires login. Local accounts in `users`, passwords hashed with Spring Security's default encoder.
- Accounts are created by an admin, no self sign-up. A user is just a username and a password, stored locally; no email, no other profile data; the app never sends email **(owner)**.
- First admin: username `admin`, password `admin`, created on first start if no admin exists; acceptable because the app runs locally only **(owner)**. Overridable with `GREEDY_ADMIN_USERNAME` / `GREEDY_ADMIN_PASSWORD`; the app logs a warning at startup while the default password is in use, and the app port is bound to `127.0.0.1` by default **(proposed)**.
- Details **(proposed)**:
  - Roles `ADMIN` and `USER`.
  - Admins create users with a temporary password that must be changed at first login, and can disable/enable users, reset passwords and delete users. Deleting a user deletes all their data after explicit confirmation; disabling keeps it.
  - Admins manage accounts, not data: they cannot open other users' files or results.
  - Users can change their own password. Passwords set in the app must meet a configurable minimum length (default 12); the default bootstrap admin is exempt. Repeated failed logins are throttled.
- Authorization: an ownership check on every dataset, run, rule, download and delete. Other users' resources answer 404, not 403, so their existence is not revealed.
- CSRF protection on every state-changing request; htmx sends the token in a header.
- Session cookie `HttpOnly`, `SameSite=Lax`, `Secure` behind HTTPS; session fixation protection (Spring default).
- Security headers: Content-Security-Policy without inline scripts (Alpine.js CSP build), `X-Content-Type-Options`, `frame-ancestors 'none'`, `Referrer-Policy`.
- Uploads: configurable size limit (legacy: 128 MB) and per-user limits on datasets, storage and queued runs. The uploaded file name is display text only, never used as a path.
- Actuator: only `/actuator/health`, without details for anonymous callers; everything else off. Error pages never show stack traces.
- Infrastructure: MongoDB with authentication; MongoDB, Spark Connect (no authentication of its own) and the Spark UIs bound to `127.0.0.1` / the internal Docker network only. The Spark UIs are tools for whoever runs the machine and are never linked from the app. Secrets come from environment variables.
- `make audit`: dependency vulnerability scan (OSV-Scanner against Gradle lockfiles).

### The algorithm as it works today (the behavior to preserve)
Builders must reproduce this exactly unless the registry says otherwise. Reference: `src/main/scala/org/kduda/greedy/algorithm/**`.

- Input CSV has a header row; all values are read as strings.
- Mode `dt`: one decision table, decision = last column. Mode `is`: one decision table per column, that column moved to the end as the decision.
- Each decision table is first de-duplicated (`dropDuplicates()`): **all counts below are over distinct rows**.
- Inconsistency removal (MCD, `DecisionTableFactory.removeInconsistenciesMCD`): decisions are ranked by frequency across the whole de-duplicated table; every group of rows with equal condition values but more than one decision is replaced by a single row carrying the highest-ranked decision among that group's decisions. Ties in frequency currently have no defined order.
- A table with at most one distinct decision yields no rules.
- For each row `r` with decision `d`, starting from `T` = the whole table and no conditions: for every unused condition attribute `a`, let `T(a)` = rows of `T` with `a = r[a]`; `N(X)` = row count, `N(X,d)` = rows with decision `d`, `M(X,d) = N(X) − N(X,d)`. Pick an attribute, add `(a = r[a])` to the rule, set `T = T(a)`, repeat until `T` has one distinct decision.
  - **M**: minimize `M(T(a), d)`.
  - **RM**: minimize `M(T(a), d) / N(T(a))`.
  - **maxCov**: `β = M(T,d) − M(T(a),d)`, `α = N(T,d) − N(T(a),d)`; among attributes with `β > 0`, minimize `α`.
  - **poly**: maximize `β / (α + 1)`.
  - **log**: maximize `β / log2(α + 2)`.
  - Ties: the earliest attribute in original column order wins (stable sort over remaining columns).
  - Arithmetic: M uses integers; RM and poly use 32-bit `Float`; maxCov stores counts in `Float` (exact for counts below 2^24); log uses `Double` with `log10(x) / log10(2)`.
- Support = number of distinct rows matching all conditions with decision `d` (= `N` of the final `T`).
- Rules are de-duplicated as **ordered** condition lists (registry item 1 changes this).
- Output order is hash-map / hash-set order, i.e. effectively arbitrary (registry item 3).
- Formats (`ToStringParser`): `csv` = `index,(a = v)(b = w) -> decision,length` (conditions concatenated with no separator, no header); `rses` = `(a=v)&(b=w)=>(dec=x[s]) s`; `csvm` = header `index,rule,coverage,length`, rows `i,(a = v) & (b = w) -> (dec = x),support,length`, then `coverage` and `length` blocks with `min,avg,max`. Index runs across all decision tables.
- Stored file name: `<upload name up to first dot>-<dt|is>-rules-<heuristic><.csv | .rul | -wmetadata.csv>`.

### Target algorithm design (Spark)
- Rules are induced for **all rows in parallel, one round per added condition**, in the style of level-wise decision-tree growth in Spark MLlib. The number of Spark jobs grows with rule length (bounded by the number of attributes), not with row count.
- State per unfinished rule: source row id, decision, chosen conditions, and the previous round's `N(T)`, `N(T,d)`. Each round: for every unfinished rule and every unused attribute, compute `N(T(a))` and `N(T(a),d)` with group-by/join; compute the heuristic score as a Spark `Column` expression; pick the best attribute per rule with a window function ordered by score then original column index (matches today's tie-break); rules whose new `T` has a single decision are finished.
- Exact math (registry #12) **(owner)**: M and maxCov compare integers; RM and poly compare fractions exactly by cross-multiplying 64-bit integer counts; log involves logarithms, so ties are detected exactly (with integer arithmetic) and non-tied scores are ordered with high-precision arithmetic. Mechanism left to the builder, preferring built-in Spark functions over UDFs.
- One engine, five score expressions. MCD and `is` mode are also pure Spark (window functions / aggregations).
- Cache or checkpoint per round to keep lineage short. Adaptive Query Execution on and shuffle partitions sized to the data, so small inputs do not spawn hundreds of tasks.
- Allowed driver-side reads: schema/metadata, single-row aggregates (stats). Everything else is a guard violation.
- Acceptance **(proposed)**: Spark jobs per run do not depend on row count; a 3-row file finishes within a few seconds on a warm Connect server (legacy: 23 s); UCI nursery (12,960 rows × 9 attributes) completes every heuristic in both modes on the local Spark services (4 cores / 8 GB).
- The exact join/grouping strategy is left to the builder, as long as the above holds and it scales with rows.

## Testing strategy

### Phase 0: safety net on the legacy code (before anything else)
The old code builds only on JDK 8 and uses MongoDB Java driver 3.2.2, so legacy work runs in Docker. Verified on 2026-10-27 with **no source changes**: build in `maven:3.8.8-eclipse-temurin-8` (`./mvnw` is unusable because `.mvn/wrapper/maven-wrapper.jar` was never committed), run on `eclipse-temurin:8-jre` against `mongo:4.4`. Uploading `paper-sample.csv` and running M / information system / RSES through the UI produced exactly `paper-sample-rules.rul`.

```bash
docker run -d --name greedy-legacy-mongo -p 127.0.0.1:27017:27017 -p 127.0.0.1:8080:8080 mongo:4.4
docker run --rm --network container:greedy-legacy-mongo -v "$PWD":/src -v greedy-legacy-m2:/root/.m2 -w /src \
  maven:3.8.8-eclipse-temurin-8 mvn -B -DskipTests package          # or `mvn -B verify` for unit + integration tests
docker run -d --name greedy-legacy-app --network container:greedy-legacy-mongo -v "$PWD":/src -w /src \
  eclipse-temurin:8-jre java -jar target/greedy-0.0.1.jar          # http://localhost:8080
docker rm -f greedy-legacy-app greedy-legacy-mongo                 # stop
```

Legacy findings from running it (each gets a characterization test; fixed in the new code):
- One run on a 3-row file takes about 23 s: 17 Spark jobs, 200 shuffle partitions per stage.
- The whole run happens inside the HTTP request; the browser waits until it finishes.
- Rule file downloads are served as `Content-Type: application/json`.
- Actuator `/env`, `/heapdump` and `/dump` are open without authentication in the default profile.

Legacy test suite (`mvn -B verify`, about 13 min): all 53 unit tests pass. `MongoSparkIT` fails under failsafe with `NoClassDefFoundError` because failsafe runs against the Spring Boot-repackaged jar (classes under `BOOT-INF/classes`); it passes when run directly with `mvn test -Dtest=MongoSparkIT`. `SampleIT` has 1 skipped test. In that run `ToStringParserTest` took 667 s, although its setup is identical to `HeuristicsMTest` (11 s); run alone it took 20 s, so the slowdown is intermittent (Spark stages occasionally sit at 0 running tasks).

Phase 0 work:
1. Coverage baseline: add JaCoCo to the legacy build (it instruments both the Java and the Scala bytecode) and record the gap list.
2. Characterization tests in the legacy code for everything uncovered: table extraction, de-duplication, MCD, each heuristic's choice and stop logic, support, all three formatters, file naming, exploration flow, storage and controllers. Edge cases: empty file, header only, single decision value, all rows identical, duplicate rows, inconsistent rows, ties in heuristic scores, ties in MCD frequencies, a single condition column, values containing spaces / commas / dots / quotes, upload names with several dots.
3. Golden corpus (the main asset, language-independent):
   - Inputs: existing fixtures; small and medium public categorical datasets (e.g. UCI lenses, zoo, car, monks-1/2/3, tic-tac-toe, with attribution and license notes in the corpus folder); a few hundred seeded random small tables generated once and committed as CSV.
   - Outputs: for every input × 5 heuristics × 2 modes × 3 formats, produced by the legacy pipeline (CSV read → table extraction → MCD → heuristic → formatter). A handful of cases also go through the real HTTP + MongoDB path to pin naming and content types.
   - Every case is generated twice with different Spark parallelism (`local[1]` and `local[4]`); cases whose output differs are flagged as nondeterministic in the corpus metadata.
   - Comparison is order-insensitive (multiset of lines; the `index` column only has to run 0..n−1), because legacy output order is arbitrary.
   - Large datasets that the legacy code is too slow for are left out of the corpus and covered by differential tests instead.
4. Coverage target **(proposed)**: ≥95% lines in the algorithm, formatter and service code, and every branch of the heuristic choose/stop logic; remaining gaps listed and explicitly accepted by the owner.
5. Owner review, then the owner tags `legacy-baseline`.

### During and after migration
- Tests move before code: each phase ports its tests first (they fail), then the code, and the phase is done only when its ported tests and the golden corpus pass.
- Differential testing: a deliberately simple in-memory reference implementation of the algorithm, written from the definitions above, lives in **test code only** and serves as the oracle. It has two arithmetic modes: legacy-compatible (`Float` for RM/poly, `log10` ratios for log) and exact. The golden corpus validates the oracle's legacy mode against real legacy output; the Spark engine must match the oracle's exact mode; so every golden difference is automatically attributed to registry #12 or flagged. ScalaCheck generates random decision tables for the engine-vs-oracle comparison, which covers what the fixed corpus cannot.
- Property tests: every rule is true on its table, covers its source row, has correct support and length; finished rules have a single decision.
- Spark execution: fast `spark-rules` tests run on an in-process local session; an integration suite runs the same code through Spark Connect against the Spark services started with Testcontainers.
- Security tests: an authorization matrix (a second user, and an admin, get 404 on every route, download and delete for the first user's data; non-admins cannot reach admin pages), forced password change, disabled users cannot log in, login throttling, CSRF, security headers, Actuator exposure, upload limits.
- Tools: ScalaTest + ScalaCheck (Scala), JUnit 5 + Kotest assertions + MockK + Spring MockMvc + Spring Security test (Kotlin), Testcontainers for MongoDB and Spark.
- Coverage: scoverage (Scala) and Kover (Kotlin), thresholds enforced by `make coverage` on `spark-rules` and the app's domain / service / security / export packages: ≥95% lines, ≥90% branches **(proposed)**. Verify both plugins work on Gradle 9; JaCoCo is the fallback.
- Linters: scalafmt + scalafix, ktlint + detekt.
- Collect guard: a build check that fails on driver-side collection (`collect`, `collectAsList`, `toLocalIterator`, `take`, `first`, `head` on Datasets, etc.) in `spark-rules`, with an explicit allowlist. Mechanism left to the builder.

## Behavior-change registry

Every intended difference from legacy output. Each is delivered as a separate, self-contained change that updates the affected golden files and references the item number, so the owner can commit it on its own. Anything not listed here must match legacy output.

| # | Change | Phase |
|---|---|---|
| 1 | Rule conditions are a set: rules with the same conditions in a different order are one rule. | 3 |
| 2 | Deterministic MCD tie-break: frequency descending, then decision value ascending. Heuristic tie-break unchanged (column order). | 3 |
| 3 | Deterministic output order: decision attribute (column order), decision, conditions. | 4 |
| 4 | Output format chosen at download time instead of at run time. | 4 |
| 5 | Stored/downloaded file name keeps the full base name (no truncation at the first dot). | 4 |
| 6 | CSV outputs quote values that need it. | 7 |
| 7 | CSV-with-metadata no longer crashes on an empty rule set. | 7 |
| 8 | RSES `.rul` gets the full header (`RULE_SET`, `ATTRIBUTES`, `DECISION_VALUES`, `RULES`), quoted names/values with spaces, one rule set per decision attribute (zip in `is` mode), RSES-compatible encoding. Legacy rule-lines-only format kept as an option. | 7 |
| 9 | Missing values `MISSING`, `NULL`, `?` handled as missing, per the RSES 2.2 guide. Exact semantics decided in Phase 7. | 7 |
| 10 | Inconsistent rows containing empty cells no longer crash the run (legacy: the MCD join drops null groups, then the heuristic loop throws `NoSuchElementException`). | 3 |
| 11 | Column names containing `.` work (legacy: Spark reads them as nested fields and fails); duplicate column names are rejected at upload with a clear message. | 4 |
| 12 | Heuristic scores compared with exact math (**owner**); legacy `Float` rounding in RM/poly and `log10`-ratio rounding in log are not reproduced, so near-equal scores may pick a different attribute than legacy. | 3 |
| 13 | A UTF-8 BOM at the start of an uploaded CSV is dropped (legacy: it stays in the first column name, so rules read `﻿a=1`; golden `edge/bom-crlf`). | 4 |
| 14 | CSV parsing follows RFC 4180: `""` inside a quoted field is one `"` (legacy: Spark 2.1's escape character is `\`, so `"say ""hi"""` stays literally, quotes included; golden `edge/quoted-values`). | 4 |
| 15 | Empty and header-only files are rejected at upload with a clear message (legacy: a 0-byte upload is refused with an unhandled `StorageException`, "Failed to store empty file", shown as HTTP 500; a header-only file is accepted and yields empty rule files and a csvm crash; golden `edge/empty-file` (pipeline only), `edge/header-only`). | 4 |
| 16 | Files of any size within the upload limit are read completely (legacy: every file over 4 MiB fails: Spark splits it into several partitions, and `SparkMongo` deletes its temporary copy after reading only the first; `SparkException` caused by `FileNotFoundException`, pinned at 4 MiB ± 1 KiB by `SparkMongoIT`). | 4 |
| 17 | The upload name is display text only (legacy: it is used as a temporary file path, so a name with a directory part makes the run fail with `Path does not exist`). | 4 |
| 18 | Rule downloads carry their format's content type (`text/csv` for csv and csvm, `text/plain` for `.rul`; legacy: `application/json`, pinned by `EndToEndGoldenIT`). | 4 |

New registry entries found during Phase 0 are proposed by the builder and approved by the owner at the Phase 0 review; #13–#18 were approved on 2026-11-07.

### Decisions from the plan review **(proposed)**
- Empty CSV cells, until #9 lands: an ordinary value, as legacy treats them everywhere except MCD. Stored as an empty string; legacy-compatible formats print it as `null`, as legacy does; grouping and joins are null-safe.
- Condition order inside a rule once conditions are a set (#1): original column order.
- Ordering of decision values in outputs: string order.
- CSV-with-metadata numbers keep legacy formatting.

## Makefile targets

| Target | Does |
|---|---|
| `help` | Lists targets (default). |
| `build` | Compile, lint, test, coverage check, package. |
| `test` / `test-unit` / `test-it` | All tests / without Docker / Testcontainers integration tests (MongoDB, Spark). |
| `test-one T=<pattern>` | Single test class or method in any module. |
| `golden` | Run the golden corpus against the new implementation. |
| `coverage` | Coverage reports for both languages, fail under thresholds. |
| `lint` / `format` | scalafmt, scalafix, ktlint, detekt (check / apply). |
| `audit` | Dependency vulnerability scan. |
| `spark-up` / `spark-down` | Local Spark services (master, worker, Connect server, History Server). |
| `spark-ui` | Print the Spark UI URLs. |
| `mongo-up` / `mongo-down` | Local MongoDB container. |
| `run` / `run-dev` | Run the app from Gradle against the local MongoDB and Spark services (started if not running); `run-dev` uses the dev profile. |
| `image` | App Docker image via `bootBuildImage`. |
| `up` / `down` | Whole stack (app, MongoDB, Spark services) via Docker Compose. |
| `deps-updates` | Report available dependency updates (Gradle versions plugin). |
| `legacy-up` / `legacy-down` / `legacy-test` / `legacy-golden` | Legacy app, tests and golden-output generation in the JDK 8 container (removed with `legacy/`). |
| `clean` | Clean all build outputs. |

## UI (Option B)

Server-rendered Thymeleaf pages; htmx for partial updates (run status via polling or the SSE extension); Alpine.js (CSP build) for small client state; Tailwind CSS 4 + daisyUI 5 built with the Tailwind standalone CLI from a Gradle task, so no Node toolchain **(proposed)**; fallback Bootstrap 5.3 if the standalone CLI cannot load daisyUI. Tabulator for the rule table (remote paging/sorting backed by MongoDB queries), Apache ECharts for heuristic comparison. Browser libraries come from WebJars (`org.webjars.npm`) with pinned versions **(proposed)**.

### User experience principles **(owner)**
- For users the app does one thing: **upload data, get rules back.** Spark, background jobs, MongoDB and the like never appear in the UI, in messages or in errors. "Run" in this document is an internal term; the UI talks about "results".
- The main path works with defaults and as few decisions as possible: choose a file, click "Find rules", watch progress, see and download the rules.
- Every choice is explained in plain words with a sensible default **(proposed)**:
  - Mode: "Predict the last column" (`dt`, default) vs. "Find rules for every column" (`is`).
  - Heuristic: a short plain-language description of each (M, RM, maxCov, poly, log), M as default.
  - Format is chosen when downloading, with a one-line explanation of each (spreadsheet CSV, CSV with statistics, RSES rule file).
- Progress is shown in plain steps ("Reading your data", "Finding rules", "Done: 42 rules found"), not as technical stages. Users can stop a search that is still running.
- Errors explain what is wrong with the user's data and how to fix it ("Row 17 has 5 values but the header has 6 columns"); internal errors show a friendly message, details only in logs.
- Upload validation happens immediately, before any work starts: file present and non-empty, header row, consistent column count, unique column names, at least one condition column and a decision column with two or more values.
- Login and account screens follow the same plain-language rules.

### Screens
1. Login, and change password (forced at first login). Admins also get user management: list users, create a user with a temporary password, disable/enable, reset password, delete (with a clear warning that the user's data goes too).
2. Home / upload: drop or choose a file, see a preview of the first rows and detected columns, pick options (defaults pre-selected), "Find rules".
3. Results: plain-language progress while working, with a stop button; when done, rule count and summary statistics, a paged/sortable/searchable rule table shown as readable "IF … THEN …" rules, download buttons for each format.
4. My data: the user's own uploaded files and their results, download original, delete with confirmation, start another search with different options.
5. Compare (secondary): results for the same file side by side (number of rules, average rule length, average coverage) as table and charts.

## Phases

### Phase 0: Safety net on legacy code
Detailed task plan: [docs/plans/phase-0.md](docs/plans/phase-0.md).

- [x] Run the legacy app as-is in Docker (no source changes); UI smoke test matches `paper-sample-rules.rul`
- [x] `make legacy-up` / `legacy-down` wrapping the commands above
- [x] Legacy test suite runs in Docker: 53/53 unit tests pass
- [x] Fix failsafe so integration tests run against `target/classes` (test-infra change only), then `make legacy-test` passes
- [x] Investigate the intermittent slowdown (`ToStringParserTest`: 667 s in the full suite, 20 s alone) so golden generation is reliable
- [x] JaCoCo coverage baseline and gap list
- [x] Characterization tests for table extraction, de-duplication, MCD
- [x] Characterization tests for each heuristic (M, RM, maxCov, poly, log) incl. ties and stop conditions
- [x] Characterization tests for formatters (csv, rses, csvm) and file naming
- [x] Characterization tests for exploration service, storage, controllers
- [x] Edge-case tests (see list above)
- [x] Golden corpus inputs: fixtures, public datasets with attribution, seeded random tables
- [x] Golden output generator (`make legacy-golden`) for 5 heuristics × 2 modes × 3 formats
- [x] Double-run nondeterminism check; flagged cases recorded
- [x] HTTP + MongoDB end-to-end golden cases
- [x] Coverage target met or gaps accepted by owner
- [ ] Owner review (incl. any new registry entries); owner tags `legacy-baseline`

### Phase 1: Spike (throwaway)
- [ ] Compose: Spark 4.2 master + worker + Connect server (with MongoDB Spark Connector, event logging) + History Server + MongoDB; all UIs reachable on localhost
- [ ] Spring Boot 4.1 app on JDK 21 with the Spark Connect client: record any classpath conflicts and fixes
- [ ] From a Spring bean, run a DataFrame job over Spark Connect that reads from and writes to MongoDB through the connector; job visible in the UIs under its run tag; cancel by tag works
- [ ] The same Scala code runs on an in-process local session (test mode) and over Spark Connect, incl. window functions and checkpoint/cache
- [ ] Decision recorded here: Spark Connect, or the fallback (`spark-submit` per run)
- [ ] Spike code deleted

### Phase 2: Skeleton
- [ ] Move the Maven project to `legacy/`; legacy targets still work
- [ ] Gradle 9 multi-module build (`spark-rules`, `app`), wrapper, version catalog, dependency locking, JDK 21 toolchain
- [ ] Compose file for MongoDB and the Spark services; `spark-up` / `spark-down` / `spark-ui`
- [ ] Makefile with all targets above
- [ ] Linters, coverage plugins and thresholds, collect guard, `make audit`
- [ ] Golden corpus runner (order-insensitive) and ported characterization tests, failing
- [ ] Remove unused legacy-only files from the new build (worksheets, `git.txt`, `logging-levels.txt`, Lombok, Spring Data REST, commons-*)

### Phase 3: Spark algorithm rewrite
- [ ] Test-only reference implementation (oracle) with legacy and exact arithmetic modes; ScalaCheck differential tests
- [ ] Oracle's legacy mode matches the golden corpus
- [ ] Property tests
- [ ] Data preparation in Spark: table extraction (`dt`, `is`), de-duplication, MCD
- [ ] Round-based induction engine with the five score expressions, exact comparison (#12)
- [ ] Support and per-run statistics
- [ ] Golden corpus passes, every difference attributed to a registry entry
- [ ] Registry #1, #2, #10, #12 applied, each as a separate change with its updated goldens
- [ ] Integration suite over Spark Connect passes
- [ ] Collect guard passes; coverage thresholds met
- [ ] Acceptance: job count independent of rows; 3-row file fast; nursery runs on the local Spark services

### Phase 4: Storage and exports
- [ ] MongoDB data model (`users`, `uploads` bucket, `datasets`, per-dataset rows collections, `runs`, `rules`), `ownerId` everywhere, indexes, MongoDB authentication
- [ ] Streaming CSV upload ingestion
- [ ] Spark reads rows and writes rules through the connector
- [ ] Streaming exports: legacy csv, csvm, rses; correct `Content-Type`; safe `Content-Disposition`
- [ ] Registry #3, #4, #5, #11 applied
- [ ] Testcontainers integration tests; golden corpus passes through the export path

### Phase 5: App and security
- [ ] Kotlin Spring Boot app, `@ConfigurationProperties` for Spark Connect and app settings
- [ ] Spring Security login with local username/password accounts and roles; first admin `admin`/`admin` (overridable by environment variables, startup warning while default)
- [ ] Admin user management (create with temporary password, disable/enable, reset password, delete with data); forced password change; own password change; login throttling
- [ ] Ownership checks on every route, download and delete (404 for others' data)
- [ ] CSRF incl. htmx, session cookie settings, security headers / CSP
- [ ] Upload size limit and per-user limits (datasets, storage, queued runs)
- [ ] Run queue: global concurrency, fairness across users, cancellation via Spark tags, restart handling, progress
- [ ] Controllers with proper 404 / validation errors, no stack traces
- [ ] Actuator health only
- [ ] Authorization matrix and other security tests; coverage thresholds met

### Phase 6: Frontend (Option B)
- [ ] Tailwind standalone CLI + daisyUI build wired into Gradle (or Bootstrap fallback)
- [ ] Login, change-password and admin user-management screens
- [ ] Home / upload screen (immediate validation with actionable messages, preview, options with plain-language explanations and defaults)
- [ ] Results screen (plain-language progress, stop button, summary, readable rule table, downloads)
- [ ] My data screen (history, download original, delete with confirmation, new search)
- [ ] Compare screen (table + charts)
- [ ] UI copy review: no internal terms (Spark, job, run, collection) anywhere users can see, incl. error pages
- [ ] MockMvc tests for pages and htmx fragments

### Phase 7: RSES and other intended changes
- [ ] Registry #6, #7
- [ ] Registry #8: full RSES `.rul` export, zip in `is` mode; round-trip parser test
- [ ] RSES `.tab` data import
- [ ] Registry #9: missing values

### Phase 8: Infra and docs
- [ ] `bootBuildImage` image; Compose file for the whole stack with pinned images, healthchecks, localhost-only ports
- [ ] README rewrite (what it does, input formats, heuristics, how to run, Spark UIs, references); keep the versions table current
- [ ] Update `AGENTS.md`
- [ ] Delete `legacy/` (golden corpus stays)
- [ ] RSES 2.2 guide: link to it or keep the PDF in the repo (owner decision; redistribution rights unclear)

### Later (not scheduled)
- [ ] Single sign-on (OIDC) as an alternative to local accounts
- [ ] Classify with induced rules (voting by support) and cross-validation, comparable with RSES
- [ ] Rseslib 3 as a test-only reference (check its GPL version against this repo's AGPL-3.0 before any runtime use)
- [ ] REST API + OpenAPI
- [ ] Command-line mode via `spark-submit` on `spark-rules`
- [ ] Numeric attributes via Spark discretization
- [ ] Browser end-to-end smoke tests

## Risks
- Spark Connect must support everything `spark-rules` needs (window functions, checkpoint/cache, the MongoDB data source, job tags, interruption). The Phase 1 spike checks this; fallback is `spark-submit` per run.
- Spring Boot 4.1 with the Spark Connect client: smaller conflict surface than embedding Spark, still checked in Phase 1.
- Resource use: app, MongoDB and four Spark services on one machine (the local Docker VM has 4 CPUs / 8 GB); worker and driver memory must be sized accordingly.
- Legacy runtime on large datasets is slow and occasionally stalls; the corpus is limited to small and medium datasets.
- Legacy output nondeterminism (MCD ties, ordering): handled by double runs, order-insensitive comparison and registry items 2–3.
- Round-based induction on wide tables: the number of (rule, attribute) pairs per round is rows × attributes; Phase 3 measures this.

## Open questions for the owner
None. Answered on 2026-10-27 **(owner)**: several users with isolated data; accounts created by an admin, username + password only, local, no email, default admin `admin`/`admin`; agents may stage (`git add`) but never commit, push or pull; one machine with a local Spark service the app connects to; exact math; no legacy deployment to migrate.

## Left to the builder
- Join/grouping strategy of the induction engine and the exact-math mechanism, within the design above.
- Exact Gradle plugin versions, Docker image tags and the collect-guard mechanism.
- Seeds, sizes and shapes of the random corpus tables.
- Page layouts and styling details.

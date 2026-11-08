# Phase 2: Skeleton — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the new build next to the legacy one: the Maven project moved to `legacy/` and still runnable, a Gradle 9 multi-module build with every quality gate (`make build` = compile, lint, collect guard, tests, coverage), the local PostgreSQL and Spark services, every `make` target from `PLAN.md`, the golden corpus runners and the ported characterization tests, which run and are reported as pending until Phases 3 and 4 make them pass.

**Architecture:** Three Gradle modules plus an included build. `spark-rules` (Scala 2.13) holds the public API of the engine as stubs (`RuleInduction.induce` throws), tested on an in-process classic Spark session (`test`) and over Spark Connect in a Testcontainers container (`integrationTest`). `app` (Kotlin, Spring Boot 4.1) is a health-only skeleton with the PostgreSQL datasource, the Spark Connect client and the legacy export API as stubs. `test-support` (Kotlin, test-only) holds what both test suites share: golden corpus access, the legacy rule-line parser, the order-insensitive comparison, the Spark Connect test container and the JUnit `@Pending` extension. `build-logic` holds the convention plugins and the collect guard. Ported tests are pending: ScalaTest `pendingUntilFixed` in Scala and `@Pending` in Kotlin both report a failing body as pending and fail once it passes, so the marker has to be removed in the change that fixes it.

**Tech Stack:** Gradle 9.9.0 (wrapper, Kotlin DSL, version catalog, dependency locking), JDK 21 toolchain, Scala 2.13.18, Spark 4.3.0 (`spark-sql-api` to compile, `spark-sql` for classic tests, `spark-connect-client-jvm` for Connect), Kotlin 2.3.21, Spring Boot 4.1.2, PostgreSQL 18.6, pgjdbc 42.7.14, `apache/spark:4.3.0-scala2.13-java21-ubuntu`, ScalaTest 3.2.19 + scalatestplus `junit-5-13` 3.2.19.0, JUnit Jupiter 6.0.3, Kotest assertions 6.2.6, Testcontainers 2.0.5, FastCSV 4.4.0, Jackson 2.21.2 (test-support), Spotless 8.11.0 (scalafmt 3.11.5, ktlint 1.8.0), scalafix plugin 0.3.0 (SemanticDB 4.17.4), detekt 1.23.8, scoverage plugin 9.1 (scalac plugin 2.5.2), Kover 0.9.11, ASM 9.11.1, ben-manes versions 0.67.0, OSV-Scanner 2.7.0. Versions checked 2026-11-08.

**Spec:** `PLAN.md`: "Phase 2: Skeleton", "Target stack", "Architecture" (Modules, Spark runtime), "Testing strategy" (During and after migration), "Makefile targets", "Behavior-change registry", "Left to the builder"; `docs/plans/phase-1-notes.md` (findings Phase 2 must apply) and the deferred items M6 and M11 in `docs/plans/phase-1-ledger.md`; `golden/README.md`.

## Global Constraints

- The legacy code is moved, not changed: nothing under `legacy/src/main/**` changes in content. Legacy test code and `pom.xml` change only for the golden corpus location (Task 1). `golden/inputs/**` and `golden/outputs/**` stay byte-identical.
- No symlinks: paths elsewhere in the repo are passed as configuration (system properties, Make variables).
- No algorithm, storage, export or security logic is written in Phase 2. Engine and export APIs are stubs (`throw new NotImplementedError("… Phase 3")` in Scala, `TODO("Phase 4")` in Kotlin); their ported tests are pending.
- `make build` passes at the end of every task. `make golden` fails until Phase 3: every compared case fails with `NotImplementedError` (exact counts in Task 9).
- Versions exactly as in the Tech Stack line, pinned in `gradle/libs.versions.toml`; Kotlin and JUnit as Spring Boot 4.1.2 manages them (2.3.21, 6.0.3). Kotlin 2.4 is out but Boot 4.1 targets 2.3; stay on 2.3.21.
- `spark-rules` compiles against `spark-sql-api` only (no RDDs, no `SparkContext`, no UDFs) and runs its tests on JUnit Platform 1.13 through the ScalaTest engine; `test-support` declares JUnit `compileOnly`, so it never pulls JUnit 6 onto the `spark-rules` classpath.
- Packages: `org.kduda.greedy.rules` (spark-rules), `org.kduda.greedy.app` (app), `org.kduda.greedy.testing` (test-support), `org.kduda.greedy.build` (build-logic).
- Style: Kotlin and `*.gradle.kts` use tabs (as the repo's Java and the Phase 1 build scripts did), enforced by ktlint through `.editorconfig`; Scala uses scalafmt's 2-space default; line length 120.
- Ports, all published on `127.0.0.1` only: PostgreSQL 5432, Spark master UI 8090, worker UI 8091, Connect gRPC 15002, driver UI 4040, History Server 18080, app 8081 (8080 stays with the legacy app until Phase 8).
- Memory on the 8 GB Docker VM: worker 2 cores / 3 GB (configurable in `.env`), Connect driver 1 GB, executor 2 GB, PostgreSQL 1.5 GB with `shared_buffers=512MB` and `max_wal_size=1GB`.
- Compose runs through `$(COMPOSE)`: `docker compose` when the plugin works, otherwise the standalone `docker-compose` (the case on this machine). Secrets live in a generated, git-ignored `.env`; `.env.example` is committed.
- Every task ends with its commit. Long runs (`make legacy-test`, first `bootBuildImage`, image pulls) go to the background with a log and `caffeinate -di -w <make pid>` (`AGENTS.md`).
- Commits: Conventional Commits on `master-ai`; scopes used here: `legacy`, `lint`, `compose`, `golden`, `rules`, `export`, `coverage`, `plan`.

## Review Focus

1. Legacy tests and golden generation after the move: until now they read the corpus at `golden/...` relative to the working directory, which becomes `legacy/`. A path still resolved the old way makes `make legacy-golden` fail after 1.5 h, or makes a test read nothing or write into `legacy/golden/`. Expected: identical results to before the move. Pinned by Task 1 (full `make legacy-test`, which includes `GoldenCorpusTest` and `EndToEndGoldenIT`, a partial golden run compared with the committed outputs, and a check that `legacy/golden` does not exist).
2. `make test-one` with a pattern that matches nothing, or matches only in one of three modules: Gradle either fails the modules without a match or passes having run nothing. Expected: exactly the matching tests run; no match is an error naming the pattern. Pinned by Task 2.
3. Empty cells: the new code stores `""` where legacy had null and printed `null`. A comparison without that mapping fails all golden cases on the 62 rule files that contain `null`; a mapping applied in the wrong place hides differences. Expected: `""` prints as `null` only in legacy notation and legacy formats. Pinned by Task 9 (`RulesSpec`) and Task 10 (`LegacyFormatsTest`).
4. Golden rule lines whose values contain `=`, `)`, `"`, `,` or spaces (`edge/quoted-values`): a naive parser splits them wrongly, and every comparison built on it is wrong. Expected: an exact round trip. Pinned by Task 6 (unit cases plus a round trip over all 1,230 golden `.rul` files).
5. Exposure: the skeleton app serving anything beyond `/actuator/health`, or listening on all interfaces outside its container. Expected: health only, details hidden, bound to `127.0.0.1`. Pinned by Task 2 (`ActuatorExposureTest`) and Task 8 (the container profile is the only one that binds `0.0.0.0`).

---

### Task 1: Move the Maven project to `legacy/`

**Files:**
- Move (`git mv`): `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/`, `src/`, `docker/`, `tmp-storage/`, `git.txt` → `legacy/`
- Create: `legacy/README.md` (three lines: what it is, run it with `make legacy-*`, deleted in Phase 8)
- Modify (after the move): `legacy/pom.xml` (surefire and failsafe `systemPropertyVariables`: `golden.root` = `${project.basedir}/../golden`), `legacy/src/test/java/org/kduda/greedy/golden/{GoldenCorpusTest,GoldenGenerator,GoldenCompare}.java`, `legacy/src/test/java/org/kduda/greedy/characterization/EndToEndGoldenIT.java`
- Modify: `Makefile` (legacy recipes), `.gitignore` (`legacy/tmp-storage/*`, `!legacy/tmp-storage/readme.txt`, `!legacy/.mvn/wrapper/maven-wrapper.jar`), `AGENTS.md` (legacy paths and commands), `golden/README.md` (`src/test/...` → `legacy/src/test/...`)

**Interfaces:**
- Produces: legacy sources under `legacy/`; `make legacy-*` unchanged in name and behaviour; the legacy jar at `legacy/target/greedy-0.0.1.jar`. The repo root is free for the Gradle build; `docker/` is free for the new Compose files.
- Produces: system property `golden.root` (the corpus directory; default `golden`, set by `legacy/pom.xml` to `../golden`). The four test classes derive their paths from it: `GoldenCorpusTest` reads `<root>/outputs`; `EndToEndGoldenIT` reads `<root>/inputs` and `<root>/outputs`; `GoldenGenerator`'s `golden.inputs` and `golden.out` default to `<root>/inputs` and `<root>/outputs`; `GoldenCompare`'s `golden.a` defaults to `<root>/outputs`. Explicit `golden.inputs`, `golden.out`, `golden.a` and `golden.b` values still win, resolved against the working directory (`legacy/`).

- [ ] **Step 1: Move.** `git mv` the listed paths. Maven runs with `legacy/` as its working directory, so the tests' relative `golden/...` paths now point at a directory that does not exist.
- [ ] **Step 2: Corpus location and Makefile.** Add `golden.root` to the four test classes and `legacy/pom.xml` as described under Interfaces. The repo root stays mounted at `/src`; `LEGACY_MVN` and `legacy-up` use `-w /src/legacy`; `legacy-test` deletes `legacy/target/jacoco.exec`; `legacy-coverage` reads `legacy/target/site/jacoco/jacoco.csv`; `legacy-golden` drops `-Dgolden.out=golden/outputs` and `-Dgolden.a=golden/outputs` for their `golden.root` defaults and keeps `target/golden-run-b` (now under `legacy/target`); its `find_mcd_ties.py` line keeps running on the host from the repo root.
- [ ] **Step 3: Full suite after the move.** `make legacy-test` (background, log, caffeinate). Expected: `BUILD SUCCESS`, 105 unit + 17 integration tests (1 skipped), "All coverage checks have been met": the totals of the last full run before the move.
- [ ] **Step 4: Partial golden run from `legacy/`.** `make legacy-test-one T=GoldenGenerator ARGS="-o '-Dgolden.include=^fixtures/complex-sample$' -Dgolden.out=target/golden-check -Dlegacy.spark.master=local[1] -Dlegacy.spark.shufflePartitions=1"`, then `make legacy-test-one T=GoldenCompare ARGS="-o -Dgolden.a=target/golden-check -Dgolden.b=golden/outputs"`. Expected: output contains `golden compare: 10 cases, 0 differences`, and `legacy/target/golden-check/nondeterministic.json` has an empty `cases` list (`complex-sample` has no MCD tie in either mode). The final `cases in both runs` assertion fails because the partial run has 10 of 1,260 cases; that failure is expected. `legacy/golden` does not exist after Steps 3 and 4 (Review Focus 1), and `git status` shows `golden/` unchanged.
- [ ] **Step 5: Docs and leftovers.** Update the legacy paths in `AGENTS.md` (Commands, Architecture, Tests, Gotchas) and `golden/README.md`; `make legacy-down`; no `greedy-legacy-mvn` container left; `git status` shows only renames plus the files listed above.
- [ ] **Step 6: Commit:** `build(legacy): move the Maven project to legacy/` (the body names the `golden.root` property and why the four test classes changed).

### Task 2: Gradle build, module skeletons, locking

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, Gradle wrapper 9.9.0 (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`, `distributionSha256Sum` set), `build-logic/{settings,build}.gradle.kts`, `build-logic/src/main/kotlin/greedy.kotlin.gradle.kts`, `build-logic/src/main/kotlin/greedy.scala.gradle.kts`
- Create: `spark-rules/build.gradle.kts`, `spark-rules/src/main/java/org/kduda/greedy/rules/{Mode,Heuristic}.java`, `spark-rules/src/main/scala/org/kduda/greedy/rules/RuleInduction.scala`
- Create: `test-support/build.gradle.kts`, `test-support/src/main/kotlin/org/kduda/greedy/testing/{Pending,PendingExtension}.kt`
- Create: `app/build.gradle.kts`, `app/src/main/kotlin/org/kduda/greedy/app/GreedyApplication.kt`, `app/src/main/resources/application.yml`
- Test: `spark-rules/src/test/scala/org/kduda/greedy/rules/{ModeAndHeuristicSpec,SparkSessionSpec}.scala`, `spark-rules/src/test/scala/org/kduda/greedy/rules/testing/TestSpark.scala`, `test-support/src/test/kotlin/org/kduda/greedy/testing/PendingExtensionTest.kt`, `app/src/test/kotlin/org/kduda/greedy/app/ActuatorExposureTest.kt`
- Modify: `Makefile` (`build`, `test-unit`, `test-one`, `lock`, `clean`), `.gitignore` (`!gradle/wrapper/gradle-wrapper.jar`, `.gradle/`, `.kotlin/`, `.env`)

**Interfaces:**
- Produces: `public enum Mode { DECISION_TABLE("dt"), INFORMATION_SYSTEM("is"); String code(); static Mode fromCode(String) }`; `public enum Heuristic { M("m"), RM("rm"), MAX_COV("maxCov"), POLY("poly"), LOG("log"); String code(); static Heuristic fromCode(String) }` (codes = legacy form values and golden file names; an unknown code throws `IllegalArgumentException` naming it).
- Produces: `object RuleInduction { def induce(data: DataFrame, mode: Mode, heuristic: Heuristic): DataFrame }`, a stub. `data` has one string column per attribute, in upload order, named as uploaded.
- Produces: `object TestSpark { lazy val session: SparkSession }`, which reads system property `greedy.spark.session` (`classic`: `master("local[2]")`, `spark.sql.shuffle.partitions=4`, UI off; `connect` arrives in Task 7).
- Produces: `@Pending(val reason: String)` (Kotlin annotation on functions and classes, meta-annotated `@ExtendWith(PendingExtension::class)`). A test that throws is reported as aborted with `pending: <reason>`; a test that passes fails with `passes now, remove @Pending("<reason>")`.
- Produces: convention plugins `greedy.kotlin` (Kotlin JVM, toolchain 21, JUnit Platform, test logging, locking) and `greedy.scala` (Scala 2.13.18, toolchain 21, ScalaTest engine `includeEngines("scalatest")`, Spark's `--add-opens` list from the Phase 1 build for classic test JVMs, `maxHeapSize = "2g"`, `-deprecation -feature -unchecked -Xlint -Wunused:imports,privates,locals -Werror`, locking). Both set system property `greedy.repo.root` (the root project directory) on every `Test` task. `build-logic` declares every third-party Gradle plugin as a dependency, with versions from the catalog, so module build files apply plugins by id without versions.

- [ ] **Step 1: Write the tests.**
  - `ModeAndHeuristicSpec`: `Mode.values.map(_.code) == Seq("dt", "is")`; `Heuristic.values.map(_.code) == Seq("m", "rm", "maxCov", "poly", "log")`; `fromCode(x.code) == x` for every value of both enums; `Heuristic.fromCode("xyz")` throws `IllegalArgumentException` whose message contains `xyz`.
  - `SparkSessionSpec`: `TestSpark.session.range(10).count() == 10L`; the session's class name is `org.apache.spark.sql.classic.SparkSession` when `greedy.spark.session` is `classic`.
  - `PendingExtensionTest` (JUnit Platform `EngineTestKit` on fixture classes tagged `fixture`, a tag the Gradle test task excludes): a `@Pending("Phase 4")` test that throws → 1 aborted, 0 failed, with reason `pending: Phase 4`; a `@Pending("Phase 4")` test that passes → 1 failed with message `passes now, remove @Pending("Phase 4")`; an unannotated passing test → 1 succeeded.
  - `ActuatorExposureTest` (`@SpringBootTest`, MockMvc, Boot 4 module names): `GET /actuator/health` → 200, JSON `status` = `UP`, no `components` and no `details`; `GET /actuator`, `/actuator/env`, `/actuator/heapdump`, `/actuator/shutdown` → 404; the `Environment` property `server.address` = `127.0.0.1`.
- [ ] **Step 2: Run `./gradlew build`.** Expected: FAIL (nothing to compile the tests against).
- [ ] **Step 3: Build files, modules, `application.yml`.** `settings.gradle.kts`: `rootProject.name = "greedy"`, `includeBuild("build-logic")` under `pluginManagement`, `include("spark-rules", "test-support", "app")`, repositories in `dependencyResolutionManagement` (Maven Central, `FAIL_ON_PROJECT_REPOS`). `spark-rules`: `compileOnly(spark-sql-api)`, `testCompileOnly(spark-sql-api)`, `testRuntimeOnly(spark-sql)` (classic), ScalaTest + scalatestplus `junit-5-13`. `app`: Spring Boot 4.1.2 (webmvc and actuator starters; the webmvc-test starter for tests); `application.yml`: `server.port: 8081`, `server.address: 127.0.0.1`, actuator exposure `health` only, discovery off, `show-details: never`, `show-components: never`. `test-support`: Kotlin, JUnit `compileOnly`; its own tests on JUnit 6.0.3 with `junit-platform-testkit`.
- [ ] **Step 4: Locking.** Both conventions and `build-logic` set `dependencyLocking { lockAllConfigurations() }` and register `resolveAndLockAll` (the Gradle documentation pattern: resolve every resolvable configuration). `make lock` = `./gradlew resolveAndLockAll --write-locks` plus the same in `build-logic` (`./gradlew -p build-logic …`). Commit the `gradle.lockfile`s.
- [ ] **Step 5: Makefile targets.** `GRADLE := ./gradlew`; `build` → `$(GRADLE) build`; `test-unit` → `$(GRADLE) test`; `clean` → `$(GRADLE) clean` and `rm -rf legacy/target`; `lock` (Step 4); `test-one T=<pattern>` → `$(GRADLE) test --tests '$(T)' -PtestOne`. With `-PtestOne`, the conventions turn off `filter.isFailOnNoMatchingTests` and `failOnNoDiscoveredTests` so modules without a match pass, and the recipe then checks that some `TEST-*.xml` under `*/build/test-results` is newer than a stamp file written before the run. If none is, it fails with `no test matched <pattern>`. Method patterns work for JUnit tests; ScalaTest specs filter by class.
- [ ] **Step 6: Run and probe.** `make build`: PASS (all four tests green). `make test-one T='*ModeAndHeuristicSpec'`: only that spec's result file is new. `make test-one T=NoSuchTest`: exit ≠ 0, `no test matched NoSuchTest` (Review Focus 2). Lock drift probe, not committed: change `scala` in the catalog to `2.13.17`, then `./gradlew :spark-rules:compileScala` must fail with a lock-state error; revert. `make lock` twice in a row leaves `git status` clean.
- [ ] **Step 7: Commit:** `build: add the Gradle build with spark-rules, test-support and app skeletons`.

### Task 3: Linters

**Files:**
- Create: `.editorconfig`, `.scalafmt.conf` (`version = 3.11.5`, `runner.dialect = scala213`, `maxColumn = 120`), `.scalafix.conf`, `config/detekt.yml`
- Modify: both convention plugins, root `build.gradle.kts` (Spotless for `*.gradle.kts` at the root and in `build-logic`, and `build-logic/src/**/*.kt`), `Makefile` (`lint`, `format`); reformat existing code

**Interfaces:**
- Produces: `make lint` = `spotlessCheck checkScalafix detekt`; `make format` = `spotlessApply scalafix`. All three are part of `check`, so `make build` enforces them.

- [ ] **Step 1: Configure.** Spotless: `scalafmt("3.11.5")` for Scala modules, `ktlint("1.8.0")` for Kotlin and `kotlinGradle`. `.editorconfig`: `[*.{kt,kts}] indent_style = tab`, `ktlint_code_style = ktlint_official`, `max_line_length = 120`; `[*.java] indent_style = tab`. scalafix with SemanticDB 4.17.4 auto-configured; `.scalafix.conf`: `rules = [DisableSyntax, RemoveUnused, OrganizeImports, NoValInForComprehension, LeakingImplicitClassVal]`, `DisableSyntax { noReturns = true, noXml = true, noFinalize = true, noValPatterns = true }`, `OrganizeImports.removeUnused = true`. detekt 1.23.8 with `buildUponDefaultConfig = true`, `config/detekt.yml` holding only overrides, and the detekt configuration's `org.jetbrains.kotlin` dependencies pinned to 2.0.21 (detekt 1.23.8 refuses another Kotlin version). If detekt 1.23.8 does not run on Gradle 9.9, use `dev.detekt` 2.0.0-alpha.6 instead and note it for `phase-2-notes.md`.
- [ ] **Step 2: Bite probes, each reverted.** A misindented Scala line → `make lint` fails in `spotlessScalaCheck`; a `return` in Scala → `checkScalafix` fails on `DisableSyntax.return`; a Kotlin file indented with spaces → `spotlessKotlinCheck` fails; `fun f(x: Int) = x * 37` in app main code → `detekt` fails on `MagicNumber`.
- [ ] **Step 3: `make format`, then `make lint` and `make build`.** Expected: PASS.
- [ ] **Step 4: Commit:** `build(lint): add scalafmt, scalafix, ktlint and detekt`.

### Task 4: Collect guard

**Files:**
- Create: `build-logic/src/main/kotlin/org/kduda/greedy/build/{CollectGuard,CollectGuardTask}.kt`, `build-logic/src/main/kotlin/greedy.collect-guard.gradle.kts`, `spark-rules/collect-guard.txt`
- Test: `build-logic/src/test/kotlin/org/kduda/greedy/build/CollectGuardTest.kt`
- Modify: `spark-rules/build.gradle.kts` (apply `greedy.collect-guard`), root `build.gradle.kts` (`check` depends on `build-logic`'s `test`), `Makefile` (`lint` adds `:spark-rules:collectGuard`)

**Interfaces:**
- Produces: `object CollectGuard { fun scan(classFiles: Sequence<Pair<String, ByteArray>>, allowlist: List<AllowEntry>): List<Finding> }`, with `data class AllowEntry(val className: String, val call: String, val reason: String, val line: Int)` and `data class Finding(val message: String)`; `fun parseAllowlist(text: String): List<AllowEntry>`. Task `collectGuard` (inputs: `spark-rules` main class directories and `collect-guard.txt`) fails with every finding, one per line; `check` depends on it.
- Rules: forbidden are calls (ASM `MethodInsnNode`) whose owner is `org/apache/spark/sql/Dataset`, `org/apache/spark/sql/classic/Dataset` or `org/apache/spark/sql/connect/Dataset` and whose name is `collect`, `collectAsList`, `toLocalIterator`, `take`, `takeAsList`, `tail`, `head`, `first`, `show` or `reduce`, plus the one-argument `pivot` of `RelationalGroupedDataset` (it collects the distinct values; `pivot` with a values list is fine). Calls inside lambdas count against their top-level class (binary name up to the first `$`). Allowlist lines are `<top-level class> <Dataset|RelationalGroupedDataset>.<method>  # <reason>`; the reason is mandatory; `#` lines and blank lines are ignored; the file ships empty apart from a header that explains the format.

- [ ] **Step 1: Write `CollectGuardTest`**, building class files with ASM's `ClassWriter`: a `Dataset.collect` call in `a/b/X` → one finding naming `a.b.X`, `Dataset.collect` and the source file and line; every forbidden name above → one finding each (parameterized); `pivot(String)` reported, `pivot(String, java.util.List)` not; `count`, `groupBy`, `cache`, `localCheckpoint`, `join` → no finding; a call inside `X.$anonfun$run$1` → reported as `X`; an allowlisted `X Dataset.collect` → no finding; an allowlist entry that matches no call → finding `stale allowlist entry (line N)`; an allowlist line without a reason → finding `allowlist line N: reason missing`.
- [ ] **Step 2: Run `./gradlew -p build-logic test`.** Expected: FAIL (no `CollectGuard`).
- [ ] **Step 3: Implement** `CollectGuard`, the task and the plugin (ASM 9.11.1 as a `build-logic` dependency).
- [ ] **Step 4: Run tests, then a bite probe (reverted).** Add `data.collect()` to `RuleInduction.induce` → `make lint` fails with a finding naming `org.kduda.greedy.rules.RuleInduction` and `Dataset.collect`. Revert; `make build` PASS.
- [ ] **Step 5: Commit:** `build: add the collect guard for spark-rules`.

### Task 5: PostgreSQL and Spark services

**Files:**
- Create: `compose.yaml`, `docker/spark/Dockerfile`, `docker/stack/make-env.sh`, `docker/stack/check.sh`, `.env.example`
- Modify: `Makefile` (`.env` rule, `db-up`, `db-down`, `spark-up`, `spark-down`, `spark-ui`, `stack-check`, `COMPOSE` detection)

**Interfaces:**
- Produces: Compose project `greedy`. `postgres` (`postgres:18.6`, db and user `greedy`, password `${GREEDY_DB_PASSWORD}`, volume `pgdata` on `/var/lib/postgresql`, `mem_limit: 1536m`, healthcheck `pg_isready`); `spark-master`, `spark-worker`, `spark-connect` and `spark-history`, all from image `greedy-spark:4.3.0` built from `docker/spark/Dockerfile`. That image is `FROM apache/spark:4.3.0-scala2.13-java21-ubuntu` plus `postgresql-42.7.14.jar` added to `/opt/spark/jars/` with `ADD --checksum=sha256:…`. The worker needs the image too, because executors run there (M11: no `--packages`). Healthchecks probe each port with bash `/dev/tcp`. Connect runs as in Phase 1 (FAIR, driver 1 g, executor 2 g, `spark.connect.grpc.binding.port=15002`, event log on a volume shared with `spark-history`) plus rolling event logs (`spark.eventLog.rolling.enabled=true`, `spark.eventLog.rolling.maxFileSize=128m`, M6). Worker cores and memory come from `.env` (`SPARK_WORKER_CORES=2`, `SPARK_WORKER_MEMORY=3g`).
- Produces: `.env` keys `GREEDY_DB_PASSWORD` (random, `openssl rand -hex 16`), `SPARK_WORKER_CORES`, `SPARK_WORKER_MEMORY`. Make generates `.env` when it is missing and never overwrites it.

- [ ] **Step 1: Write `check.sh`**, printing one `OK`/`FAIL` line per check and exiting non-zero on any failure: `pg_isready` in `postgres`; HTTP 200 from 8090, 8091, 4040 and 18080; TCP on 15002; `/opt/spark/jars/postgresql-42.7.14.jar` present in `spark-worker` and `spark-connect`; the driver's REST API (`/api/v1/applications/<id>/environment` on 4040) reports `spark.scheduler.mode=FAIR` and `spark.eventLog.rolling.enabled=true`.
- [ ] **Step 2: `make stack-check` with nothing running.** Expected: FAIL on the first check.
- [ ] **Step 3: Write `compose.yaml`, the Dockerfile, `make-env.sh`, `.env.example` and the targets.** `db-up` / `spark-up` = `$(COMPOSE) up -d --wait <services>`; `db-down` / `spark-down` stop and remove only their services (volumes kept); `spark-ui` prints the four UI URLs and `sc://localhost:15002`.
- [ ] **Step 4: `make db-up spark-up && make stack-check`.** Expected: all OK; `docker stats --no-stream` within the memory budget. With `.env` moved aside temporarily, `make db-up` writes a new one; restore the original afterwards.
- [ ] **Step 5: `make spark-down db-down`.** Expected: no `greedy-` containers running; the volumes remain.
- [ ] **Step 6: Commit:** `build(compose): add PostgreSQL and the Spark services`.

### Task 6: Golden corpus helpers

**Files:**
- Create: `test-support/src/main/kotlin/org/kduda/greedy/testing/golden/{GoldenCorpus,GoldenFiles,LegacyRul,InputTables}.kt`
- Test: `test-support/src/test/kotlin/org/kduda/greedy/testing/golden/{GoldenFilesTest,LegacyRulTest,GoldenCorpusTest}.kt`

**Interfaces:**
- Produces: `class GoldenCorpus(val root: Path)` with `companion object { fun fromSystemProperties(): GoldenCorpus }` (root = `<greedy.repo.root>/golden`), `fun cases(): List<GoldenCase>`, `fun mcdTies(): Set<InputMode>`, `fun input(id: String): Path`, `fun output(case: GoldenCase, format: String): Path?`; `data class GoldenCase(val input: String, val mode: String, val heuristic: String, val outputs: Map<String, String>, val error: String?, val formatErrors: Map<String, String>)` with `val id = "$input $mode $heuristic"`; `data class InputMode(val input: String, val mode: String)`. Formats are the manifest keys `csv`, `rses`, `csvm`. JSON through Jackson 2 databind's tree model.
- Produces: `object GoldenFiles { fun normalize(fileName: String, content: String): List<String> }`, a line-for-line port of `legacy/src/test/java/org/kduda/greedy/golden/GoldenFiles.java`.
- Produces: `data class LegacyCondition(val attribute: String, val value: String)`; `data class LegacyRule(val conditions: List<LegacyCondition>, val decisionAttribute: String, val decision: String, val support: Long)`; `object LegacyRul { fun parse(content: String): List<LegacyRule>; fun renderLine(rule: LegacyRule): String; fun render(rules: List<LegacyRule>): String }`. A line is `(a=v)&(b=w)=>(dec=x[s]) s`. Parsing: the decision part follows the last `=>(`; the decision attribute runs to its first `=`; the value runs to the last `[`; the support sits between `[` and `]) `, and the trailing support must be equal to it. Conditions are the text before that point without the outer parentheses, split on `)&(`; each attribute runs to its first `=`. `render` joins the lines with a trailing `\n` each.
- Produces: `data class InputTable(val columns: List<String>, val rows: List<List<String>>)`; `object InputTables { fun read(path: Path): InputTable }` (FastCSV 4.4.0, RFC 4180, UTF-8, BOM dropped, empty cells as `""`; an empty file throws `IllegalArgumentException("empty file: <path>")`).

- [ ] **Step 1: Write the tests.**
  - `GoldenFilesTest`: the seven cases of `legacy/src/test/java/org/kduda/greedy/golden/GoldenFilesTest.java`, inputs and expected outcomes verbatim.
  - `LegacyRulTest`: `(B=0)&(A=0)=>(D=y[1]) 1` parses to conditions `[B=0, A=0]`, `D`, `y`, `1`; `(a="say ""hi""")=>(d=n[1]) 1` keeps the value `"say ""hi"""` verbatim; `(a=x,1)&(b=p q)=>(d=y[2]) 2` keeps `x,1` and `p q`; `(a=f(x))&(b=1=2)=>(d=y[1]) 1` gives `f(x)` and `1=2`; `(a=1)=>(d=y[1]) 2` throws `IllegalArgumentException` (support mismatch); `""` parses to an empty list; `render(parse(s)) == s` for each of these lines.
  - `GoldenCorpusTest`: 1,260 cases; the cases with `error` are exactly `edge/dotted-names`, `edge/empty-cells` and `edge/empty-file` × `dt`/`is` × 5 heuristics (30); `formatErrors` occur only under `csvm`, in 340 cases; every output named in the manifest exists. Every input-mode in `nondeterministic.json` is in `mcd-ties.json` (port of the legacy test). Every golden `.rul` file (1,230) satisfies `LegacyRul.render(LegacyRul.parse(content)) == content` (Review Focus 4). Every input except `edge/empty-file` reads into a table whose rows all have `columns.size` values; `edge/bom-crlf`'s first column is `a` (no BOM); `edge/empty-file` throws.
- [ ] **Step 2: Run `./gradlew :test-support:test`.** Expected: FAIL (no implementation).
- [ ] **Step 3: Implement** the four files.
- [ ] **Step 4: Run.** Expected: PASS; `make build` PASS.
- [ ] **Step 5: Commit:** `test(golden): add the golden corpus helpers for the new build`.

### Task 7: Integration suites over Spark Connect and PostgreSQL

**Files:**
- Create: `test-support/src/main/kotlin/org/kduda/greedy/testing/containers/SparkConnectContainer.kt`
- Test: `app/src/test/kotlin/org/kduda/greedy/app/{ApplicationIT,SparkConnectIT}.kt`
- Modify: `TestSpark.scala` (`connect` session), `spark-rules/build.gradle.kts` (`integrationTest`: the same specs as `test` on the Connect client runtime), `app/build.gradle.kts` (datasource, PostgreSQL driver, Spark Connect client, `implementation(project(":spark-rules"))`, `integrationTest`, `bootRun` JVM args), `app/src/main/resources/application.yml`, `app/src/main/resources/application-dev.yml`, `ActuatorExposureTest.kt`, `Makefile` (`test`, `test-it`, `run`, `run-dev`; `test-one` adds `integrationTest`)

**Interfaces:**
- Consumes: `docker/spark/Dockerfile` (Task 5), `greedy.repo.root` (Task 2).
- Produces: `class SparkConnectContainer` (Testcontainers `GenericContainer` from an image built out of `docker/spark/Dockerfile`, running the Connect server with `--master local[2]`, `spark.ui.enabled=false` and `spark.sql.shuffle.partitions=4`; port 15002 exposed; waits for the log line `Spark Connect server started`), `fun connectUrl(): String` (`sc://<host>:<mapped port>`), `companion object { val shared: SparkConnectContainer }`, started lazily once per JVM.
- Produces: `integrationTest` tasks in `spark-rules` and `app` (in `app`: classes named `*IT`, which `test` excludes; both use `--add-opens=java.base/java.nio=ALL-UNNAMED`). The app's datasource is `spring.datasource.url: ${GREEDY_DB_URL:jdbc:postgresql://localhost:5432/greedy}`, user `${GREEDY_DB_USER:greedy}`, password `${GREEDY_DB_PASSWORD:}`. The Spark client excludes `org.apache.logging.log4j:log4j-slf4j2-impl` (Phase 1). `bootRun` gets the same `--add-opens`. Profile `dev`: Spring Boot devtools (`developmentOnly`) and DEBUG logging for `org.kduda.greedy`.

- [ ] **Step 1: Write the tests.** `SparkSessionSpec` (Task 2) asserts `org.apache.spark.sql.connect.SparkSession` when `greedy.spark.session` is `connect`. `ApplicationIT` (`@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@ServiceConnection` `PostgreSQLContainer("postgres:18.6")`): `GET /actuator/health` → 200, `status` `UP`; `GET /actuator/env` → 404. `SparkConnectIT`: a session on `SparkConnectContainer.shared.connectUrl()` returns `range(10).count() == 10L`, and a 3-row DataFrame from `createDataFrame` comes back with the same rows (Arrow both ways). `ActuatorExposureTest` now sets `management.health.db.enabled=false`, because it runs without a database.
- [ ] **Step 2: `make test-it`.** Expected: FAIL (no container class, no datasource).
- [ ] **Step 3: Implement**, then the targets: `test-it` → `$(GRADLE) integrationTest`; `test` → `$(GRADLE) test integrationTest`; `run` / `run-dev` → `db-up spark-up` first, then `.env` loaded (`set -a; . ./.env; set +a`) and `$(GRADLE) :app:bootRun`, with the `dev` profile for `run-dev`. If Testcontainers cannot reach Colima's Docker, export `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` from the Makefile, and note it for `AGENTS.md`.
- [ ] **Step 4: Run.** `make test-it`: PASS in both modules; `SparkSessionSpec` passes in `test` (classic) and in `integrationTest` (Connect). `make run` in the background, then `curl -s http://127.0.0.1:8081/actuator/health` → `{"status":"UP"}`; stop it. `make build` PASS.
- [ ] **Step 5: Commit:** `test: run the integration suites over Spark Connect and PostgreSQL`.

### Task 8: App image and the whole stack

**Files:**
- Modify: `app/build.gradle.kts` (`bootBuildImage`), `compose.yaml` (service `app` in profile `app`), `app/src/main/resources/application-container.yml`, `Makefile` (`image`, `up`, `down`)

**Interfaces:**
- Produces: image `greedy-app:dev`, built with `BP_JVM_VERSION=21`, `BPE_DELIM_JAVA_TOOL_OPTIONS=" "` and `BPE_APPEND_JAVA_TOOL_OPTIONS=--add-opens=java.base/java.nio=ALL-UNNAMED`. Compose service `app`: `profiles: [app]`, `SPRING_PROFILES_ACTIVE=container`, `GREEDY_DB_URL=jdbc:postgresql://postgres:5432/greedy`, `GREEDY_DB_PASSWORD` from `.env`, port `127.0.0.1:8081:8081`, `depends_on` `postgres` and `spark-connect` healthy. Profile `container` sets `server.address: 0.0.0.0`, the only place that does (Review Focus 5).

- [ ] **Step 1: `make up` before implementing.** Expected: FAIL (no image, no `app` service).
- [ ] **Step 2: Implement.** `image` → `$(GRADLE) :app:bootBuildImage` (the first run pulls the Paketo builder: background, log, caffeinate); `up` → `image`, then `$(COMPOSE) --profile app up -d --wait`, then poll `http://127.0.0.1:8081/actuator/health` for up to 120 s; `down` → `$(COMPOSE) --profile app down` (volumes kept).
- [ ] **Step 3: Run.** `make up`: health `UP`; `docker logs` of the app shows `Picked up JAVA_TOOL_OPTIONS` containing `--add-opens=java.base/java.nio=ALL-UNNAMED`; `docker-compose ps` shows every service healthy or running. `make down`: no `greedy-` containers left.
- [ ] **Step 4: Commit:** `build(image): build the app image and run the whole stack`.

### Task 9: Ported engine tests and the golden runner (pending until Phase 3)

**Files:**
- Create: `spark-rules/src/main/scala/org/kduda/greedy/rules/RuleColumns.scala`
- Test: `spark-rules/src/test/scala/org/kduda/greedy/rules/testing/{Tables,Rules}.scala`, `.../rules/testing/RulesSpec.scala`, `.../rules/PreparationSpec.scala`, `.../rules/HeuristicChoiceSpec.scala`, `.../rules/golden/GoldenRulesSpec.scala`
- Modify: `spark-rules/build.gradle.kts` (`test` and `integrationTest` exclude `org.kduda.greedy.rules.golden.*`; new task `golden`, classic runtime, only that package, not part of `check`), `Makefile` (`golden`)

**Interfaces:**
- Consumes: `RuleInduction`, `Mode`, `Heuristic`, `TestSpark` (Task 2); `GoldenCorpus`, `InputTables`, `LegacyRul`, `GoldenFiles` (Task 6).
- Produces: `object RuleColumns { val DecisionAttribute = "decision_attribute"; val Conditions = "conditions"; val Attribute = "attribute"; val Value = "value"; val Decision = "decision"; val Support = "support"; val schema: StructType }`, the output contract of `induce`: `decision_attribute string`, `conditions array<struct<attribute string, value string>>` (in chosen order), `decision string`, `support long`, all non-null. Phase 3 may add columns.
- Produces (test helpers): `Tables.table(header: String, rows: String*): DataFrame` (comma-split, string columns, empty field = `""`); `Tables.fromInput(t: InputTable): DataFrame`; `Rules.render(rules: DataFrame): Seq[String]` (notation `B=0 & A=0 => D=y [1]`, conditions in stored order, `""` printed as `null`, sorted); `Rules.toLegacy(rules: DataFrame): Seq[LegacyRule]` (with the same `""` → `null` mapping).
- Porting rule: legacy expectations are copied verbatim. Where the legacy expectation is a crash or an undefined choice, the test asserts the registry's target instead, and its name starts with the registry number. Every ported test body is wrapped in `pendingUntilFixed`.

- [ ] **Step 1: Write `RulesSpec`** (not pending; it tests the helper on a hand-built rules DataFrame of `RuleColumns.schema`): conditions keep their stored order; rules from two decision attributes appear together, sorted; a `""` condition value and a `""` decision print as `null` (Review Focus 3); `toLegacy` maps `""` to `null` the same way.
- [ ] **Step 2: Write `HeuristicChoiceSpec`**, a port of `legacy/src/test/java/org/kduda/greedy/characterization/HeuristicChoiceTest.java`. Its nine legacy tests keep their tables and expected lists verbatim (through `Tables.table`, mode `DECISION_TABLE`, `Rules.render`). Its crash test becomes `registry #10: an empty condition in an inconsistent group no longer crashes`: for every heuristic, `table("A,D", ",y", ",n", "1,n")` yields no rules (MCD gives the `""` group decision `n`, leaving one decision value).
- [ ] **Step 3: Write `PreparationSpec`**, a port of `DecisionTablePreparationTest` expressed through rules, using `Heuristic.M` unless stated otherwise:
  ```scala
  // MCD counts distinct rows: raw n=4, y=3; distinct y=3, n=2
  table("A,D", "0,y", "0,n", "1,n", "1,n", "1,n", "2,y", "3,y") -> Seq("A=0 => D=y [1]", "A=1 => D=n [1]", "A=2 => D=y [1]", "A=3 => D=y [1]")
  table("A,D", "0,y", "0,n", "1,n", "2,n", "3,y")               -> Seq("A=0 => D=n [1]", "A=1 => D=n [1]", "A=2 => D=n [1]", "A=3 => D=y [1]")
  // "registry #2: MCD ties go to the smallest decision value" (legacy: undefined)
  table("A,D", "0,y", "0,n", "1,y", "2,n")                      -> Seq("A=0 => D=n [1]", "A=1 => D=y [1]", "A=2 => D=n [1]")
  // information system: one table per column, that column last, the others in upload order; ties go to the earliest; every heuristic
  table("B,A,C", "0,0,x", "1,1,y"), INFORMATION_SYSTEM          -> Seq("A=0 => B=0 [1]", "A=1 => B=1 [1]", "B=0 => A=0 [1]", "B=0 => C=x [1]", "B=1 => A=1 [1]", "B=1 => C=y [1]")
  table("A,D", "0,y", "1,y")                                    -> Seq()  // one decision value: no rules
  table("A,B,D")                                                -> Seq()  // header only
  ```
- [ ] **Step 4: Check the derived legacy expectations against the legacy code.** The first, second and fourth `PreparationSpec` lists are derived by hand. Check them with a scratch JUnit test in `legacy/src/test/java/org/kduda/greedy/characterization/ScratchCheckTest.java` (through `LegacyTables` and `LegacyRules`; the fourth one with every heuristic) run by `make legacy-test-one T=ScratchCheckTest`, then delete the file. Registry-target lists cannot be checked against legacy; their test names carry the registry number.
- [ ] **Step 5: Write `GoldenRulesSpec`**, registering one ScalaTest test per case, named by `GoldenCase.id`:
  - a case with `error` → `cancel("legacy failed: <error>")`;
  - an input in `KnownDifferences = Map("edge/bom-crlf" -> 13, "edge/quoted-values" -> 14, "edge/duplicate-names" -> 11)` → `cancel("registry #N changes how this input is read")`. These goldens reflect legacy CSV reading, which the new upload path replaces. Phase 4 updates their goldens and removes the entries;
  - otherwise: `InputTables.read`, then `Tables.fromInput`, then `RuleInduction.induce(df, Mode.fromCode(mode), Heuristic.fromCode(heuristic))`, then `Rules.toLegacy`, then `LegacyRul.render`. It passes when `GoldenFiles.normalize("x.rul", rendered) == GoldenFiles.normalize("x.rul", <golden .rul>)`. On a mismatch, the message lists up to 10 missing and 10 extra lines, plus `MCD tie in this input-mode (registry #2)` when `mcdTies()` contains it.
- [ ] **Step 6: Run.** `make build`: PASS, with `RulesSpec` green and every `HeuristicChoiceSpec` and `PreparationSpec` test reported pending. `make test-it`: the same specs pending over Connect. `make golden` (= `$(GRADLE) :spark-rules:golden`): exit ≠ 0 with 1,260 tests: 1,200 failed (`NotImplementedError`), 60 cancelled (30 legacy errors, 30 known differences). Record the run time.
- [ ] **Step 7: Commit:** `test(rules): port the engine characterization tests and add the golden runner`.

### Task 10: Ported export tests (pending until Phase 4)

**Files:**
- Create: `app/src/main/kotlin/org/kduda/greedy/app/export/{Rule,LegacyFormat,LegacyFormats}.kt`
- Test: `app/src/test/kotlin/org/kduda/greedy/app/export/{LegacyFormatsTest,LegacyFileNamesTest,GoldenFormatsTest}.kt`
- Modify: `Makefile` (`golden` also runs `:app:test --tests 'org.kduda.greedy.app.export.GoldenFormatsTest'`)

**Interfaces:**
- Consumes: `Mode`, `Heuristic` (Task 2); `GoldenCorpus`, `LegacyRul`, `GoldenFiles`, `@Pending` (Tasks 2, 6).
- Produces: `data class Condition(val attribute: String, val value: String)`; `data class Rule(val decisionAttribute: String, val conditions: List<Condition>, val decision: String, val support: Long)`; `enum class LegacyFormat(val suffix: String, val contentType: String) { CSV(".csv", "text/csv"), CSV_WITH_METADATA("-wmetadata.csv", "text/csv"), RSES(".rul", "text/plain"); fun fileName(uploadName: String, mode: Mode, heuristic: Heuristic): String }` (`fileName` is a stub); `object LegacyFormats { fun write(format: LegacyFormat, rules: Sequence<Rule>, out: Appendable) }` (a stub; it writes the rules in the order given).

- [ ] **Step 1: Write `LegacyFormatsTest`**, a port of `OutputFormatTest` with every test `@Pending("Phase 4")` and the expected text verbatim. `twoRules` = `[Rule(D, [B=0, A=0], y, 1), Rule(D, [A=1], n, 1)]`:
  - rses: `"(B=0)&(A=0)=>(D=y[1]) 1\n(A=1)=>(D=n[1]) 1\n"`;
  - csv: `"0,(B = 0)(A = 0) -> y,2\n1,(A = 1) -> n,1\n"`;
  - csv with metadata: `"index,rule,coverage,length\n0,(B = 0) & (A = 0) -> (D = y),1,2\n1,(A = 1) -> (D = n),1,1\ncoverage\nmin,avg,max\n1,1.0,1\nlength\nmin,avg,max\n1,1.5,2\n"`;
  - the index continues across decision attributes: `[Rule(B, [A=0], x, 1), Rule(A, [B=x], 0, 1)]` → csv `"0,(A = 0) -> x,1\n1,(B = x) -> 0,1\n"`;
  - `csv does not quote values containing commas (registry #6, Phase 7)`: `[Rule(D, [A=a,b], y, 1)]` → `"0,(A = a,b) -> y,1\n"`;
  - `csv with metadata fails on an empty rule set (registry #7, Phase 7)`: `shouldThrowAny`;
  - rses and csv of no rules are `""`;
  - new, from the `PLAN.md` decision on empty cells: `[Rule(D, [A=""], y, 1)]` → rses `"(A=null)=>(D=y[1]) 1\n"` (Review Focus 3).
- [ ] **Step 2: Write `LegacyFileNamesTest`.** The content types (`text/csv`, `text/csv`, `text/plain`) are checked now, without `@Pending`. File names, ported from `ExplorationServiceTest` under `@Pending("Phase 4")`, as (upload name, `Mode`, `Heuristic`, `LegacyFormat`): (`my.data.v2.csv`, `DECISION_TABLE`, `M`, `CSV`) → `my-dt-rules-m.csv` (truncated at the first dot, registry #5 changes it in Phase 4); (`paper.csv`, `INFORMATION_SYSTEM`, `MAX_COV`, `RSES`) → `paper-is-rules-maxCov.rul`; (`paper.csv`, `DECISION_TABLE`, `POLY`, `CSV_WITH_METADATA`) → `paper-dt-rules-poly-wmetadata.csv`; (`data`, `DECISION_TABLE`, `M`, `CSV`) → `data-dt-rules-m.csv`; (`.hidden.csv`, `DECISION_TABLE`, `M`, `CSV`) → `-dt-rules-m.csv`.
- [ ] **Step 3: Write `GoldenFormatsTest`** (one `@Pending("Phase 4")` test). For every case without `error`, it parses the golden `.rul` (`LegacyRul.parse`), converts to `Rule` (`null` → `""`) and writes every format the manifest lists for that case. The normalized output must equal the normalized golden file (`GoldenFiles.normalize`). For each format under `formatErrors`, `write` must throw. The test collects every mismatch and fails listing the first 20.
- [ ] **Step 4: Implement the stubs.** Run `make build`: PASS, with these tests pending and the content-type test green. `make golden` now also runs `GoldenFormatsTest` (pending).
- [ ] **Step 5: Not ported, recorded in `phase-2-notes.md` with the reason.** `WebLayerTest`, `StorageIT`, `StorageValidationTest`, `SparkMongoIT` and `EndToEndGoldenIT` pin legacy HTTP and GridFS behaviour that Phases 4–5 replace; their replacements are the Phase 4–5 security, upload and export tests. `ExplorationServiceTest`'s fallbacks for unknown or missing form values cannot occur with typed `Mode`/`Heuristic`. The 2017 unit tests on `paper-sample`, `complex-sample` and `empty.csv` are covered by the golden fixture cases and `edge/header-only`. `SparkTestConfigTest` and `LegacyRulesTest` test legacy test infrastructure.
- [ ] **Step 6: Commit:** `test(export): port the legacy format tests as pending`.

### Task 11: Coverage and thresholds

**Files:**
- Modify: `build-logic/src/main/kotlin/greedy.scala.gradle.kts` (scoverage), `app/build.gradle.kts` (Kover), `Makefile` (`coverage`)

**Interfaces:**
- Produces: scoverage 2.5.2 on `spark-rules` with checks Line ≥ 0.95 and Branch ≥ 0.90; Kover on `app` over packages `org.kduda.greedy.app.domain`, `.service`, `.security` and `.export`, rule `minBound(95, LINE)` and `minBound(90, BRANCH)`. Both checks are part of `check`. `make coverage` runs the reports and checks and prints the report paths (`spark-rules/build/reports/scoverage/index.html`, `app/build/reports/kover/html/index.html`). `test-support` and `build-logic` have no threshold (test and build code).

- [ ] **Step 1: Configure,** then `make coverage`. Expected: PASS; the pending tests execute the stubs. If generated data-class members count against the Kover threshold, cover them with tests; do not exclude real code to pass.
- [ ] **Step 2: Bite probes, each reverted.** Add an uncalled public method with a branch to `RuleInduction` → `checkScoverage` fails; delete `LegacyFileNamesTest` → `koverVerify` fails naming the rule and the measured value.
- [ ] **Step 3: `make build`.** Expected: PASS.
- [ ] **Step 4: Commit:** `build(coverage): enforce scoverage and Kover thresholds`.

### Task 12: Audit and dependency updates

**Files:**
- Modify: `build-logic/build.gradle.kts` and root `build.gradle.kts` (versions plugin, stable versions only), `Makefile` (`audit`, `deps-updates`)

**Interfaces:**
- Produces: `make audit` = `docker run --rm -v "$(CURDIR)":/src:ro -w /src ghcr.io/google/osv-scanner:v2.7.0 scan source` with `-L <lockfile>` for each `git ls-files '*gradle.lockfile'` outside `legacy/`; it exits non-zero when it finds vulnerabilities. `make deps-updates` = `$(GRADLE) dependencyUpdates` (`rejectVersionIf` drops alpha, beta, RC, M, SNAPSHOT and preview candidates).

- [ ] **Step 1: `make audit`.** Expected: a findings table, or none. If the image has no arm64 build, ask the owner before installing `osv-scanner` another way. Findings are recorded in `phase-2-notes.md` and not suppressed; ignoring or fixing them is the owner's call.
- [ ] **Step 2: `make deps-updates`.** Expected: a report that works with the lockfiles (resolve any conflict between the plugin and locking).
- [ ] **Step 3: Commit:** `build: add make audit and make deps-updates`.

### Task 13: Docs, decisions and final verification

**Files:**
- Create: `docs/plans/phase-2-notes.md`, `docs/plans/phase-2-ledger.md` (a copy of the execution ledger, if one was kept)
- Modify: `PLAN.md`, `README.md`, `AGENTS.md`, `golden/README.md`, `Makefile` (help text)

- [ ] **Step 1: Every `PLAN.md` target exists.** `for t in help build test test-unit test-it golden coverage lint format audit spark-up spark-down spark-ui db-up db-down run run-dev image up down deps-updates legacy-up legacy-down legacy-test legacy-golden clean; do make -n $t >/dev/null || echo MISSING $t; done; make -n test-one T=x >/dev/null || echo MISSING test-one` prints nothing, and `make help` lists each one with a one-line description.
- [ ] **Step 2: Legacy leftovers stay out of the new build.** `rg -n 'lombok|data-rest|commons-(lang3|configuration2|io)' --glob '*.kts' --glob '*.toml'` finds nothing; `git ls-files | rg -v '^legacy/' | rg 'worksheet|git\.txt|logging-levels'` finds nothing.
- [ ] **Step 3: Write `phase-2-notes.md`.** Cover what was built, the pinned versions, plugin compatibility (detekt choice, scoverage and Kover on Gradle 9.9), Testcontainers on Colima, the audit findings, timings (`make build`, `make test-it`, `make golden`, the first `make image`), the tests not ported and why (Task 10, Step 5), and open points for Phase 3.
- [ ] **Step 4: Update the docs.** `PLAN.md`: tick the seven Phase 2 boxes; add `test-support` and `build-logic` to Modules; record the pending mechanism (testing strategy), the collect-guard mechanism (resolves that "Left to the builder" item), app port 8081, Kotlin tab indentation, and PostgreSQL 18.6 pinned in Target stack. `README.md`: replace the TBDs this phase pinned (Gradle 9.9.0, PostgreSQL 18.6, ScalaTest 3.2.19, Kotest assertions 6.2.6, scoverage 2.5.2, Kover 0.9.11, scalafmt 3.11.5 / scalafix, ktlint 1.8.0 / detekt). `AGENTS.md`: the two builds, layout, commands, environment (Compose, `.env`, Testcontainers), gotchas found in this phase. `golden/README.md`: a section on the new runners (`make golden`, `GoldenRulesSpec`, `GoldenFormatsTest`, known differences).
- [ ] **Step 5: Final verification.** `make clean build` PASS; `make test-it` PASS; `make golden` fails exactly as in Task 9, Step 6; `make lint`, `make coverage` PASS; `make db-up spark-up && make stack-check` OK, then `make spark-down db-down`; `make legacy-test` PASS (background, caffeinate); no orphaned `caffeinate`, no `greedy-legacy-mvn`, no `greedy-` containers left running.
- [ ] **Step 6: Commit:** `docs(plan): record Phase 2`.

## Greedy algorithms implementation

#### Directories

* `spark-rules/` – Scala 2.13 module with the Spark rule-induction engine and its public API (stubs until Phase 3).
* `app/` – Kotlin Spring Boot web app: UI, security, uploads, PostgreSQL access, exports (a health-only skeleton for now).
* `test-support/` – Kotlin library used only by the other modules' tests (e.g. the `@Pending` marker for tests whose code arrives in a later phase).
* `build-logic/` – Gradle included build with the convention plugins (`greedy.kotlin`, `greedy.scala`, `greedy.collect-guard`) the modules apply, and the collect guard: an ASM bytecode scan that fails the build when `spark-rules` collects Spark data to the driver (allowlist: `spark-rules/collect-guard.txt`).
* `config/` – linter configuration: `detekt.yml` (overrides of detekt's defaults); scalafmt, scalafix and ktlint settings are the dotfiles in the repo root (`.scalafmt.conf`, `.scalafix.conf`, `.editorconfig`).
* `gradle/` – Gradle version catalog (`libs.versions.toml`) and wrapper.
* `golden/` – the golden corpus: what the 2017 code produces, which every new implementation is checked against (see `golden/README.md`).
* `golden/inputs/` – input CSV files: legacy test fixtures, public UCI datasets, seeded random tables, edge cases.
* `golden/outputs/` – legacy rule files for every input, mode, heuristic and format, with the manifest and the nondeterminism reports.
* `golden/scripts/` – Python scripts that produced the public and random inputs and the list of most-common-decision ties.
* `legacy/` – the 2017 Maven project (Spring Boot 1.4, Spark 2.1), run in Docker through `make legacy-*` to regenerate golden outputs; deleted in Phase 8.
* `docs/plans/` – plans, notes, ledgers and measurements of each modernization phase ([PLAN.md](PLAN.md) is the overview).
* `.claude/` – Claude Code project settings (permission rules for agents).


#### Versions: legacy vs. current

Legacy = what the project was built with in 2017 (resolved from the Maven build). Current = target of the modernization in [PLAN.md](PLAN.md); "TBD" is pinned when that phase lands, "—" means not used.

| Name | Type | Legacy version | Current version |
|---|---|---|---|
| JDK | Runtime | 1.8 | 21 |
| Java | Language (app code) | 8 | — (replaced by Kotlin) |
| Kotlin | Language (app code) | — | 2.3.21 |
| Scala | Language (Spark code) | 2.11.8 | 2.13.18 (as built by Spark 4.3.0) |
| Apache Spark (spark-core, spark-sql) | Framework | 2.1.0 | 4.3.0 |
| Spark runtime | Infrastructure | embedded in the app, `local[4]` | separate local Spark services (standalone master, worker, Spark Connect server, History Server), official `apache/spark` image |
| Spark Connect client | Library | — (app was the Spark driver) | 4.3.0 |
| Spring Boot | Framework | 1.4.3.RELEASE | 4.1.2 |
| Spring Framework | Framework | 4.3.5.RELEASE | 7.0.10 |
| Spring Data MongoDB | Library | 1.9.6.RELEASE | — (replaced by PostgreSQL access through Spring `JdbcClient` or Spring Data JDBC, TBD) |
| Spring Data REST | Library | 2.5.6.RELEASE | — (removed) |
| Spring Security | Library | — (no security) | managed by Spring Boot 4.1.2 |
| Spring Boot Actuator | Library | 1.4.3.RELEASE | 4.1.2 (health only) |
| Embedded Tomcat | Server | 8.5.6 | 11.0.27 |
| Servlet API | API | javax.servlet 3.1 | jakarta.servlet 6.1.0 |
| Thymeleaf | Template engine | 2.1.5.RELEASE | 3.1.5.RELEASE |
| Bootstrap (WebJar) | Frontend library | 3.3.7-1 | — (replaced by Tailwind CSS + daisyUI) |
| jQuery (WebJar) | Frontend library | 3.1.1-1 | — (replaced by htmx + Alpine.js) |
| Tailwind CSS | Frontend library | — | 4.x (TBD) |
| daisyUI | Frontend library | — | 5.x (TBD) |
| htmx | Frontend library | — | 2.x (TBD) |
| Alpine.js | Frontend library | — | 3.x (TBD) |
| Tabulator | Frontend library | — | TBD |
| Apache ECharts | Frontend library | — | TBD |
| WebJars locator | Library | webjars-locator 0.32 | webjars-locator-lite 1.1.5 |
| MongoDB server | Database | `latest` (unpinned; legacy verified on 4.4) | — (replaced by PostgreSQL) |
| PostgreSQL | Database | — | 18.x, or 19 once released (TBD, pinned) |
| MongoDB Java driver | Database driver | mongodb-driver 3.2.2 | — (replaced by the PostgreSQL JDBC driver) |
| PostgreSQL JDBC driver | Database driver | — | 42.7.14 |
| MongoDB Spark Connector | Library | — | — (Spark's built-in JDBC data source) |
| Flyway | Library | — | managed by Spring Boot 4.1 (TBD) |
| Upload storage | Infrastructure | MongoDB GridFS | PostgreSQL large objects (originals) + one rows table (Phase 1) |
| tus (resumable uploads) | Library | — (single multipart request) | tus-java-server 1.0.0-3.3 |
| FastCSV | Library | — (Spark's CSV reader) | 4.4.0 |
| Hadoop client | Library | 2.2.0 | 3.5.0 (as bundled by Spark 4.3.0) |
| Jackson | Library | 2.6.7 (forced for Spark) | 3.1.7 (app) + 2.x required by Spark |
| Janino | Library | 3.0.6 (forced for Spark) | as required by Spark 4.3 |
| Logback | Library | 1.1.8 | 1.5.38 |
| SLF4J | Library | 1.7.22 | 2.0.20 |
| Lombok | Library | 1.16.12 | — (removed; Kotlin) |
| Apache Commons Lang 3 | Library | 3.5 | — (removed) |
| Apache Commons Configuration 2 | Library | 2.1 | — (replaced by Spring `@ConfigurationProperties`) |
| Apache Commons IO | Library | 2.5 | — (removed) |
| Maven (wrapper) | Build tool | 3.3.9 | — (replaced by Gradle) |
| Gradle | Build tool | — | 9.9.0 (wrapper), Kotlin DSL |
| Make | Build tool | — | Makefile wrapping Gradle |
| maven-scala-plugin | Build plugin | 2.15.2 | — (Gradle `scala` plugin) |
| maven-failsafe-plugin | Build plugin | 2.19.1 | — (Gradle test suites) |
| docker-maven-plugin (Spotify) | Build plugin | 0.4.11 | — (Spring Boot `bootBuildImage`) |
| JUnit | Test framework | 4.12 | JUnit Jupiter 6.0.3 |
| ScalaTest | Test framework | — | 3.2.19, on JUnit Platform 1.13 through scalatestplus `junit-5-13` 3.2.19.0 |
| ScalaCheck | Test library | — | TBD |
| Mockito | Test library | 1.10.19 | — (replaced by MockK) |
| MockK | Test library | — | TBD |
| AssertJ | Test library | 2.5.0 | — (replaced by Kotest assertions) |
| Kotest assertions | Test library | — | 6.2.6 |
| Testcontainers | Test library | — | 2.0.5 |
| scoverage | Coverage (Scala) | — | TBD |
| Kover | Coverage (Kotlin) | — | TBD |
| scalafmt / scalafix | Code quality (Scala) | — | 3.11.5 / plugin 0.3.0 (SemanticDB 4.17.4) |
| ktlint / detekt | Code quality (Kotlin) | — | 1.8.0 / 1.23.8 (via Spotless 8.11.0) |
| Docker base image | Infrastructure | openjdk:8-jre-alpine | Paketo buildpacks via `bootBuildImage` |
| Docker Compose file | Infrastructure | format version 2 | Compose Specification |

#### Development
* To enable Spring Boot Live Reload for Scala in IntelliJ:
  * Go to `Setting` -> `Build, Executions, Deplyment` -> `Compiler` -> enable `Build projects automatically`
  * Press Ctrl+Alt+Shift+/ -> `Registry` -> enable `compiler.automake.allow.when.app.running`
  * Restart application

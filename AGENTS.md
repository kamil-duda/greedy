# AGENTS.md

This file provides guidance to AI coding agents working with code in this repository.

## What this is

A Spring Boot web app that induces decision rules from tabular data with greedy heuristics (M, RM, maxCov, poly, log), in the rough-set tradition of RSES. A user uploads a CSV, picks a heuristic, a data interpretation and an output format, and downloads the generated rule file. The README itself warns that the implementation is inefficient and uses Spark poorly.

## Current state: modernization in progress

`PLAN.md` is the source of truth for the rewrite (Gradle/Kotlin app, Scala 2.13 + Spark 4.2 via Spark Connect, multi-user security). Read it before changing anything:
- No algorithm or business-logic changes until Phase 0 (characterization tests + golden corpus on the legacy code) is ticked off and reviewed.
- Every intended change to rule output goes into the behavior-change registry in `PLAN.md` and is delivered as a separate, self-contained change with its updated golden files, so the owner can commit it on its own.
- Tick `PLAN.md` checkboxes as work lands; keep the legacy-vs-current versions table in `README.md` current.

## Git: agents commit on `master-ai`, the owner pushes

Agents may commit, but only on branch `master-ai` (check with `git status -sb`; on any other branch, stop and ask). Agents never run `git push` or `git pull` and never rewrite or move history (`commit --amend`, `reset`, `rebase`, `checkout`, `switch`, `branch`, `tag`, …); all of these except `--amend` are blocked by deny rules in `.claude/settings.json`, so a fix to an earlier commit is a new commit. When commits are ready to go out, ask the owner to push. Staging with `git add` and read-only commands (`git status`, `git diff`, `git log`, `git show`, `git blame`) are fine; ask before anything that could discard work (`git checkout -- <file>`, `git clean`, `git stash drop`). Commit only files you changed or generated; leave the owner's unstaged edits out unless asked. A behavior change from the registry in `PLAN.md` is always its own commit with its updated golden files.

## Long-running work: check on it

Anything that runs for minutes (full suite, golden generation, Docker builds) runs in the background with output redirected to a log file, and the agent checks on it periodically instead of waiting for the end. Every few minutes: is the log still growing (`ls -la <log>`, `tail`), is the container using CPU (`docker stats --no-stream`)? If nothing has been written for about 5 minutes and the step is not known to be that slow, treat it as stuck: take a thread dump (see Gotchas), find the cause, and say so. Never sit on a silent job. Expected durations: `make legacy-test` about 5–6 min; `LegacyPipelineMatchesServiceTest` about 2.5 min; golden generation about 1 h 45 min (run A 1.5 h, run B 13 min), logging one line per case (some share a line with Spark output, so count them with an unanchored `rg -o 'golden [a-z]*/'`).

### Keep the Mac awake, and leave nothing behind

The owner's MacBook idle-sleeps 1 minute after the display turns off (`pmset -g`: `sleep 1`, `displaysleep 5`); a dark screen alone is only display sleep, real sleep shows in `pmset -g log | rg 'Entering Sleep|Wake from'`. Claude Code's own `caffeinate -i -t 300` covers only the minutes when the agent is active: on 2026-11-07 the Mac slept between monitor events and paused the golden run for 331 s. So every long job gets its own `caffeinate` tied to it: start the job in the background, take the PID of its `make` process (`pgrep -fl 'make legacy-'`), then run `caffeinate -di -w <pid>` in the background (`-d` keeps the display on too, at the owner's request; `-i` alone lets it go dark and lock); it exits with the job. Never start `caffeinate` without `-w <pid>` or `-t <seconds>`. Closing the lid still sleeps the Mac (except in clamshell mode on AC).

Orphaned `caffeinate` processes have kept the Mac awake with no Claude session running. At session start, when a long job ends and before stopping work, check `ps -A -o pid,ppid,etime,args | awk '$4 == "caffeinate"'` (and `pmset -g assertions | rg -A1 caffeinate`). Expected: `caffeinate -i -t 300` whose parent is a live `claude` process, and `caffeinate -di -w <pid>` while `<pid>` runs. Anything else (no `-t`/`-w`, a `-w` PID that is gone, a parent that is gone past its `-t`) is an orphan: `kill` it and tell the owner. Likewise, with no job running `docker ps` shows no `greedy-legacy-mvn`; remove a leftover one with `docker rm -f greedy-legacy-mvn`.

## Environment

Docker runs in Colima profile **`docker`** (4 CPUs, 8 GB), which is where `DOCKER_HOST` points. After a reboot start it with `colima start docker`; plain `colima start` starts the small `default` profile instead. The Maven cache lives in the `greedy-legacy-m2` volume.

## Toolchain

Legacy stack: Spring Boot 1.4.3, Java 8, Scala 2.11.8, Spark 2.1.0, MongoDB Java driver 3.2.2 (GridFS), Thymeleaf 2 with Bootstrap 3 / jQuery webjars. It builds only on JDK 8 (Scala 2.11.8 and Spring Boot 1.4 predate JDK 9+) and needs an old MongoDB server (4.4 verified; current versions fail with the 3.2.2 driver). `./mvnw` is unusable because `.mvn/wrapper/maven-wrapper.jar` was never committed, and `docker/*.sh` / `docker/greedy/docker-compose.yml` use `mongo:latest`. Run everything in Docker instead.

## Commands

Run from the repo root; `make` lists all targets. The Docker recipes live in the `Makefile`.

```bash
make legacy-test                                              # unit + integration tests + 95% line-coverage check, about 5–6 min (starts MongoDB 4.4 if needed)
make legacy-test-one T=HeuristicsMTest#shouldReturnValidRules # one test; comma-separate classes; *IT classes work too
make legacy-build                                             # target/greedy-0.0.1.jar, tests skipped
make legacy-up                                                # app on http://localhost:8080
make legacy-down                                              # stop app and MongoDB
make legacy-test ARGS='-Dlegacy.spark.master=local[1] -Dlegacy.spark.shufflePartitions=4'   # extra Maven args; test-only Spark settings
make legacy-test-one T=HeuristicChoiceTest ARGS=-o            # -o: Maven offline, faster once dependencies are cached
make legacy-coverage                                          # tests + JaCoCo report (target/site/jacoco/index.html), per-package summary
make legacy-golden ARGS=-o                                    # regenerate golden/outputs: run A local[4], run B local[1], compare; about 1 h 45 min
make legacy-test-one T=GoldenGenerator ARGS="-o '-Dgolden.include=^public/lenses$' -Dgolden.out=target/golden-check"   # partial golden run; quote the regex
```

Test JVMs get their Spark master, shuffle partitions and locality wait from the `legacy.spark.*` Maven properties (`src/test/resources/spark.properties` reads `${sys:greedy.spark.master}`), plus fixed limits on Spark's UI history (`spark.ui.retained*`, without them a long-lived test JVM fills its heap); the app's own `src/main/resources/spark.properties` is unchanged.

`verify` (so `make legacy-test` and `make legacy-coverage`) fails when any `org.kduda.greedy.algorithm*` or `org.kduda.greedy.service*` package drops below 95% line coverage; the accepted gaps are listed in `docs/plans/phase-0-notes.md`. `make legacy-test` deletes `target/jacoco.exec` first, because JaCoCo appends across runs.

No linter or formatter is configured. Java uses tabs; Scala uses 2-space indentation.

Search text with `rg` (ripgrep), never `grep`: `grep`, `egrep`, `fgrep`, `zgrep` and `git grep` are denied in `.claude/settings.json`. `rg` takes extended regexes by default (no `-E`), skips git-ignored files such as `target/` unless given `--no-ignore` or an explicit path, and also works at the end of a pipe (`docker ps | rg greedy`). `pgrep` looks up processes, not text, and stays allowed. The Makefile's `help` target still uses `grep` internally; that is fine.

Profiles in `application.yml`: default (only the `/actuator` hypermedia endpoint is off; see Gotchas), `dev` (devtools restart also watches `src/main/scala`, `/shutdown` enabled without auth; in Docker pass `-e SPRING_PROFILES_ACTIVE=dev`), `container` (MongoDB at `mongodb://greedy-mongo`). Live reload of Scala in IntelliJ needs "Build project automatically" plus the `compiler.automake.allow.when.app.running` registry flag (see README).

## Architecture

One Maven module with two languages:
- `src/main/java`: the Spring layer (controllers, exploration service, repositories over GridFS).
- `src/main/scala`: Spark access and the algorithms. Java calls Scala `object`s directly (e.g. `HeuristicsM.calculateDecisionRules(...)`), so `scala.collection.immutable.Map`/`List` and `scala.Tuple2` appear in Java signatures and tests.
- `src/main/scala/worksheet`: scratch worksheets, not application code.

### Storage

Uploaded data and generated rules both live in MongoDB GridFS, behind `MongoGridFsStorageService`. The kind of file is stored in GridFS metadata: `type` is `data` or `association-rules` (`GreedyTableTypes`), and rule files carry `parent` = id of the data file they came from. `DataFileRepository` and `RulesFileRepository` are thin filters over that metadata; deleting a data file also deletes its rule files by `parent`.

### Exploration pipeline (`DataExplorationService.exploreAndSave`)

1. `SparkMongo` copies the GridFS file to `tmp-storage/<original filename>`, reads it with Spark (`header=true`, every column a string), caches it and deletes the temp file.
2. Type `dt`: the file is one decision table whose last column is the decision. Type `is` (information system): `DecisionTableFactory.extractDecisionTables` builds one decision table per column, moving that column to the end.
3. `DecisionTableFactory.removeInconsistenciesMCD` collapses rows with equal conditions but different decisions to the most common decision.
4. `createMapOf` keys the tables by decision column name.
5. The chosen heuristic produces rules, `ToStringParser` formats them, and the result is stored as `<data file name up to first dot>-<type>-rules-<heuristic><ext>`.

Form values flow from `explore.html` through `ExploreRequestModel` into string switches in `DataExplorationService`: heuristics `m|rm|maxCov|poly|log`, type `dt|is`, output `csv|rses|csvm` (`csvm` adds coverage/length statistics).

### Heuristics (`algorithm/{m,rm,maxcov,poly,log}`)

All five are copies of one greedy loop and differ only in the per-column score and whether the minimum or maximum wins. For each row, start from its decision and repeatedly add the condition `column = row's value` with the best score, narrowing to rows that match the rule so far, until those rows share a single decision. Rules are de-duplicated with a `HashSet`, then support is added: rows matching both conditions and decision (the RSES definition). A table with at most one distinct decision yields no rules. Spark is only used up to `dt.collect()`; the heuristics themselves run on an in-memory `Array[Row]` on the driver.

Rule representation from the heuristics onward: `Map[decisionAttribute, List[List[(String, String)]]]`, where each rule is `("support", n) :: (decisionAttribute, value) :: conditions`. `ToStringParser` depends on these positions.

### Spark

`GreedySparkInstance` is a Scala `object` (one per JVM, created on first use) configured from the classpath `spark.properties` through commons-configuration, not through Spring. Classes get it by mixing in the `SparkAware` trait. Tests have their own `src/test/resources/spark.properties`.

## Tests

- The original unit tests extend `SpringUnitTest` (`@SpringBootTest`), so even pure algorithm tests start the full Spring context and Spark.
- Integration tests (`*IT`) extend `SpringIntegrationTest` and write to a real GridFS.
- Fixtures are in `src/test/resources/files`; `paper-sample*` and `complex-sample*` come with expected rules in `.csv` and `.rul` form.
- Phase 0 characterization tests (`src/test/java/org/kduda/greedy/characterization`) pin current behavior, bugs included. Algorithm and formatter tests skip Spring and use `GreedySparkInstance` directly through the helpers in `characterization/support`: `LegacyTables` (inline tables, the service's `dt`/`is` preparation), `LegacyRules` (rules as `B=0 & A=0 => D=y [1]`) and `LegacyPipeline` (the service's steps without MongoDB). Web and storage tests use MockMvc and real MongoDB (`*IT`).
- `golden/` is the language-independent corpus (see `golden/README.md`): 126 inputs (fixtures, public UCI sets, 100 seeded random tables, 15 edge cases) and the legacy outputs for every input × mode × heuristic × format, with `outputs/manifest.json` (per-case outputs, or the exception class) and `outputs/nondeterministic.json` (cases that differed between run A and run B). `make legacy-golden` regenerates it through `GoldenGenerator` and `GoldenCompare` (test classes run only by name). Outputs are compared with `GoldenFiles.normalize` (order-insensitive). Every nondeterministic case is a most-common-decision tie (registry #2), and the list is a lower bound. `EndToEndGoldenIT` replays four cases through HTTP and MongoDB. `golden/.gitattributes` stops git from converting line endings, because `golden/inputs/edge/bom-crlf.csv` must keep CRLF.

## Gotchas

- Failsafe 2.19.1 puts the Boot-repackaged jar (classes under `BOOT-INF/classes`) on the integration-test classpath even when `classesDirectory` is set; `pom.xml` adds `target/classes` via `additionalClasspathElements`. Without it, `*IT` fail with `NoClassDefFoundError`.
- Exploring runs synchronously inside `POST /explore/{id}` and takes 20+ s even for 3 rows (200 shuffle partitions). Browser automation (`playwright-cli` is installed) needs timeouts above its 5 s default, or wait for `Job complete!` in the app log.
- `TestRestTemplate` form POSTs on this stack send `Content-Length` twice, and Tomcat 8.5 answers 500 before any filter or controller runs (visible only with `-Dlogging.level.org.apache.coyote=DEBUG`). Post forms with a plain `HttpURLConnection`, as `EndToEndGoldenIT.explore` does; multipart uploads and GETs are fine.
- Tests that run without a Spring context must not log at DEBUG: Spark floods stdout, surefire falls behind and the test JVM blocks (looks like a Spark stall). `src/test/resources/logback-test.xml` keeps the root level at WARN.
- Thread dump of a hanging test: `docker exec greedy-legacy-mvn ps -eo pid,args | rg surefirebooter` lists two processes; take the PID of the `java` line, not the `/bin/sh -c` wrapper, then `docker exec greedy-legacy-mvn jstack <pid>` (or `jstat -gc <pid>`). Attaching to the wrapper makes the shell dump `core` and leaves `.attach_pid<pid>` in the repo root; delete both. `pgrep -f surefirebooter` inside `sh -c` matches the shell itself.
- Rule output order is hash-based and arbitrary: compare rule files order-insensitively, e.g. `diff <(sort a) <(sort b)`.
- Actuator `/env`, `/heapdump` and `/dump` are open without auth in the default profile; never expose the legacy app beyond localhost.

## RSES

Output `rses` targets the RSES 2.2 `.rul` format. Appendix A of the RSES 2.2 User's Guide defines the `.tab` data and `.rul` rule formats; a copy is in the repo root (`RSES_2.2_User’s_Guide.pdf`); the original is at https://www.mimuw.edu.pl/~szczuka/rses/RSES_doc_eng.pdf. That server answers 403 to curl/WebFetch, but a headless browser such as `playwright-cli` can fetch it. `ToStringParser.buildStringRSES` writes only rule lines (no `RULE_SET` / `ATTRIBUTES` / `DECISION_VALUES` / `RULES` header) and, in `is` mode, mixes several decision attributes in one file. RSES has not been developed since 2005; its maintained successor is Rseslib 3 (https://github.com/awojna/Rseslib).

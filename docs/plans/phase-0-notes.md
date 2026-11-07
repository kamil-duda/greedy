# Phase 0 notes

Findings log for [Phase 0](phase-0.md). Newest facts first within each section. Every ruling made while executing the plan, with its evidence: [phase-0-ledger.md](phase-0-ledger.md).

## Summary for the owner's review

Phase 0 (all tasks done 2026-11-07) pins what the 2017 code does, bugs included, before any logic changes. Open for you: accept the coverage gaps, approve or reject registry #13–#18, then commit and tag `legacy-baseline`.

**What is pinned.** `make legacy-test` (about 5.5 min, all in Docker) runs 119 tests: the original 53 unit + 4 integration tests (1 skipped, as before) and 62 new ones.

| Area | Tests | Classes |
|---|---|---|
| Algorithms, formats, service (no Spring: `DecisionTablePreparationTest`, `HeuristicChoiceTest`, `OutputFormatTest`, `ExplorationServiceTest`, `LegacyPipelineMatchesServiceTest`, `SparkTestConfigTest`, `LegacyRulesTest`) | 36 | 7 |
| Web, storage, end-to-end (`WebLayerTest` with MockMvc; `StorageIT`, `SparkMongoIT`, `EndToEndGoldenIT` on MongoDB 4.4) | 21 | 4 |
| Golden comparison (`GoldenFilesTest`) | 5 | 1 |

**Coverage.** `algorithm*` + `service*`: lines 571/572 (99.8%), branches 160/198 (80.8%); baseline 2026-10-27 had `service.exploration` at 9% and `service.storage` at 50% of lines. `verify` now fails below 95% lines per package. Every remaining miss is unreachable or compiler-generated (list under Coverage gaps).

**Golden corpus.** 126 inputs (5 fixtures, 6 UCI datasets, 100 seeded random tables, 15 edge cases) × 2 modes × 5 heuristics = 1,260 cases, 3,350 output files (33 MB). 30 cases fail outright (`dotted-names`, `empty-cells`, `empty-file`), csvm fails in 340 case-formats on empty rule sets (registry #7). 42 input-modes differ between the `local[4]` and `local[1]` runs, all from most-common-decision ties (registry #2); 110 input-modes contain such a tie, all listed in `golden/outputs/mcd-ties.json`, so the flagged list is a lower bound. Four cases also go through HTTP + MongoDB (`EndToEndGoldenIT`).

**Surprises** (details below): large uploads fail after the temporary copy is deleted; upload names are used as file paths; csvm crashes on every rule-less run; the BOM stays in the first column name; doubled quotes are not unescaped; duplicate column names are silently renamed to `a0`, `a1`; downloads are served as `application/json`; storage only logs a failed GridFS validation.

**Slowdowns, both test-infrastructure, no source change.** The 2026-10-27 "Spark stall" was stdout backpressure from DEBUG logging in tests without Spring (fixed by `logback-test.xml`). The golden run's growing slowness was Spark's UI listeners retaining per-task metrics in one long-lived JVM (fixed by test-only `spark.ui.retained*` limits).

**Proposed registry entries** #13–#18 (BOM, RFC 4180 quotes, empty/header-only uploads, large files, upload name as path, content types): see the last section and `PLAN.md`.

## Coverage baseline

Measured 2026-10-27 with `make legacy-coverage` (JaCoCo 0.8.12, unit + integration tests: 54 unit, 3 integration run + 1 skipped), before any Phase 0 characterization tests except `SparkTestConfigTest`.

| Package | Lines | Branches |
|---|---|---|
| `org.kduda.greedy` | 33.3% | - |
| `org.kduda.greedy.algorithm` | 100.0% | 80.0% |
| `org.kduda.greedy.algorithm.log` | 96.3% | 59.4% |
| `org.kduda.greedy.algorithm.m` | 100.0% | 70.8% |
| `org.kduda.greedy.algorithm.maxcov` | 96.2% | 55.9% |
| `org.kduda.greedy.algorithm.parser` | 100.0% | 50.0% |
| `org.kduda.greedy.algorithm.poly` | 96.2% | 59.4% |
| `org.kduda.greedy.algorithm.rm` | 93.8% | 61.5% |
| `org.kduda.greedy.algorithm.util` | 100.0% | 100.0% |
| `org.kduda.greedy.controller` | 64.9% | 100.0% |
| `org.kduda.greedy.domain` | 41.2% | - |
| `org.kduda.greedy.exception` | 0.0% | - |
| `org.kduda.greedy.model` | 21.4% | 40.0% |
| `org.kduda.greedy.repository` | 0.0% | 0.0% |
| `org.kduda.greedy.repository.data` | 8.8% | 0.0% |
| `org.kduda.greedy.repository.rules` | 9.7% | 0.0% |
| `org.kduda.greedy.service.exploration` | 9.0% | 0.0% |
| `org.kduda.greedy.service.storage` | 50.0% | 25.0% |
| `org.kduda.greedy.spark` | 100.0% | - |
| `org.kduda.greedy.spark.generic` | 100.0% | - |
| `org.kduda.greedy.spark.reader.csv` | 100.0% | 100.0% |
| `org.kduda.greedy.spark.reader.mongo` | 92.0% | - |

Branch percentages for Scala code include compiler-generated branches (pattern matches, `for` desugaring), so they read lower than the logic warrants.

### Gap list: `algorithm*` and `service*` (per source file, line numbers in the file)

| File | Lines | Missed lines | Why |
|---|---|---|---|
| `service/exploration/DataExplorationService.java` | 6/67 (9.0%) | 48–146 (all of `exploreAndSave` and helpers) | No test calls the service. Task 6. |
| `service/storage/MongoGridFsStorageService.java` | 23/46 (50.0%) | 41–42, 67–70, 76–78, 91–112 | Validation failure path, find by type, all deletes untested. Task 7. |
| `algorithm/rm/HeuristicsRM.scala` | 38/41 (92.7%) | 53, 74–75 | 53: `RM = 0.0F` after `RM == Float.PositiveInfinity`, unreachable (the source row always matches its own value, so `N(T(a)) ≥ 1`). 74–75: path where a rule needs another condition; no existing test needs one. Task 5. |
| `algorithm/poly/HeuristicsPoly.scala` | 43/45 (95.6%) | 82–83 | Same multi-condition path. Task 5. |
| `algorithm/maxcov/HeuristicsMaxCov.scala` | 43/45 (95.6%) | 83–84 | Same. Task 5. |
| `algorithm/log/HeuristicsLog.scala` | 44/46 (95.7%) | 83–84 | Same. Task 5. |
| `algorithm/m/HeuristicsM.scala` | 39/39 (100%) | – | Multi-condition path covered by `HeuristicsMComplexSampleTest`. |
| `algorithm/parser/ToStringParser.scala` | 68/68 (100%) | – | Partial branches on lines 17, 22, 48, 52, 85, 94. |
| `algorithm/DecisionTableFactory.scala` | 94/94 (100%) | – | Partial branches on lines 57, 86. |

## Intermittent slowdown

**Update 2026-10-28: cause found and fixed (test configuration only).** During Task 6 the four new characterization test classes hung for over 40 minutes. Thread dumps showed:
- the test JVM's Spark threads parked inside `Logging.logDebug` and the main thread waiting on a Spark job: it looked stalled, not busy;
- in the Maven JVM, surefire's single output reader (`ThreadedStreamConsumer`) busy in `StatelessXmlReporter.createOutErrElements`, copying a finished test class's captured output into its XML report;
- the Maven log at 96 MB, no longer growing.

Tests that do not start a Spring context get logback's default level, DEBUG, so Spark logs megabytes per job (including generated code). While surefire's one reader thread is busy with a report, nobody drains the test JVM's stdout, the pipe fills, and every thread that logs blocks. That is the "Spark stage at 0 running tasks" symptom. Fix: `src/test/resources/logback-test.xml` with root level WARN; Spring Boot tests still apply `logging.level.*` from the test `application.yml`. The same four classes then ran in 35 s with a 12 KB log. The 667 s run on 2026-10-27 was very likely the same mechanism: the original suite also logs at DEBUG until the first Spring context reconfigures logging. A thread dump of that run is not available.

Correct dump command, using the surefire JVM's PID from `ps`: `docker exec greedy-legacy-mvn ps -eo pid,args | rg surefirebooter` (two lines: use the `java` one, not the `/bin/sh -c` wrapper; attaching to the wrapper dumps `core` into the repo root), then `docker exec greedy-legacy-mvn jstack <pid>`. The `pgrep -f surefirebooter` form given below matches its own `sh -c` wrapper.

**Earlier conclusion (2026-10-27): not reproduced; no setting changed.** One full run on 2026-10-27 took 12:50 min, with `ToStringParserTest` alone at 667 s and Spark stages sitting at 0 running tasks. Eight later full runs show no stall:

| Run | Conditions | Total | `ToStringParserTest` | Slowest class |
|---|---|---|---|---|
| Task 1 verify | MongoDB only | 2:03 | – | – |
| Task 1 done | MongoDB only | 2:12 | – | – |
| Task 2 coverage | MongoDB only, JaCoCo agent | ~2:10 | – | – |
| A1–A3 | MongoDB only | 133 s, 128 s, 127 s | 9 s, 8 s, 9 s | `MostCommonDecisionTest` 23–28 s |
| B1–B2 | legacy app running in the same network namespace (as in the slow run) | 110 s, 109 s | 7 s, 7 s | `MostCommonDecisionTest` 22–23 s |

Per-class times varied by at most a few seconds across runs (data: `docs/plans/phase-0-data/t3-times.tsv`). Ruled out as sufficient causes: the legacy app's idle Spark session sharing the network namespace (B runs), memory pressure (other containers: `elasticsearch` capped at 780 MiB of the 8 GB Docker VM).

Remaining hypotheses, untested because the symptom cannot be triggered: (1) Spark delay scheduling: "0 running tasks while tasks are pending" is what `spark.locality.wait` (3 s per locality level) looks like when cached blocks report a host that does not match the local executor; (2) transient contention on the Docker VM host. Changing `spark.locality.wait` without the symptom would be a guess, so the default stays.

If it recurs: while the slow class is running, take two thread dumps 30 s apart with `docker exec greedy-legacy-mvn sh -c 'jstack $(pgrep -f surefirebooter | head -1)'` and look at `TaskSchedulerImpl`/`TaskSetManager` and GC threads; then rerun with `make legacy-test ARGS='-Dlegacy.spark.localityWait=0'`. The golden generator (Task 9) logs per-case durations, so a stall during golden generation is visible.

## Golden corpus run

Generated 2026-11-07 with `make legacy-golden ARGS=-o`: 126 inputs × 2 modes × 5 heuristics = 1,260 cases per run.

| Run | Spark | Duration |
|---|---|---|
| A → `golden/outputs` | `local[4]`, 200 shuffle partitions (legacy defaults) | 1:30 h, of which 331 s the Mac was asleep; by group: edge 234 s, fixtures 288 s, public 668 s, random 4,236 s |
| B → `target/golden-run-b` | `local[1]`, 1 shuffle partition | 12:46 min |
| Comparison | | 5 s |

Run B is 7× faster: on these small tables the 200 shuffle partitions per stage dominate.

**Outputs.** 1,230 cases succeed; 3,350 output files, 33 MB (largest: `fixtures/tic-tac-toe/is-maxCov-wmetadata.csv`, 2 MB).

**Error cases** (no outputs at all), all in `edge`:

| Exception | Input | Cases | Cause |
|---|---|---|---|
| `org.apache.spark.sql.AnalysisException` | `dotted-names` | 10 | Spark reads `a.b` as a nested field (registry #11) |
| `java.util.NoSuchElementException` | `empty-cells` | 10 | MCD drops the null-condition group, the heuristic runs out of columns (registry #10) |
| `java.lang.UnsupportedOperationException` | `empty-file` | 10 | the 0-byte file has no header row; pipeline only: the legacy app already refuses a 0-byte upload (`StorageIT.emptyUploadIsRejected`) |

**Format errors** (one format fails, the others are written): only csvm, always `java.lang.UnsupportedOperationException` (`empty.min`, registry #7), whenever a run produces no rules at all: 340 case-formats in 68 input-modes. Edge 45 (`header-only`, `identical-rows`, `single-condition`, `single-row` in both modes; `single-decision` in `dt`), fixtures 5 (`paper-sample` in `dt`), random 290 (58 input-modes of 31 tables, small tables whose decision attributes are left with a single value after MCD). In `is` mode, csvm fails only when every attribute has no rules (`single-decision` in `is` succeeds).

**Nondeterminism.** `golden/outputs/nondeterministic.json` lists 630 format-level differences: 210 cases, i.e. 42 input-modes, each with all 5 heuristics × 3 formats differing. Non-random: `edge/mcd-tie` (dt, is), `edge/numeric-like` (is), `fixtures/sample` (is), `public/car` (is), `public/lenses` (is), `public/monks-3` (is). Random tables: 003 (is), 006 (dt, is), 011, 016 (is), 023 (dt, is), 026, 028, 029, 030, 034, 035, 036 (is), 043 (dt, is), 047, 051, 056, 060, 061, 064, 066, 073, 080 (is), 083 (dt, is), 085 (is), 087 (dt, is), 091, 095, 096 (is), 099 (dt, is).

The cause is the MCD tie-break (registry #2), not the heuristics: every flagged input-mode has an inconsistent group whose decisions tie in table-wide frequency, and Spark's sort order picks the winner (`edge/mcd-tie`: run A keeps `(a=0)=>(d=n)`, run B `(a=0)=>(d=y)`). The double run is a lower bound: `golden/scripts/find_mcd_ties.py` finds 68 more input-modes with such a tie that happened to resolve the same way in both runs. All 110 are in `golden/outputs/mcd-ties.json`, with the tied group and decisions (incl. `fixtures/paper-sample` in `is`, whose output still equals the 2017 `paper-sample-rules.rul`); `GoldenCorpusTest` checks that every flagged case is among them. A later implementation in legacy mode may legitimately differ from the golden file on any of the 110; registry #2 replaces the tie-break anyway.

**Edge inputs (Review Focus).** `bom-crlf`: the BOM stays in the first column name (`﻿a`) in all three formats; CRLF leaves no `\r` in values. `unicode`: names and values survive unchanged. `quoted-values`: `"x,1"` is unquoted to `x,1` (and printed unquoted in both CSV formats, registry #6), but `"say ""hi"""` is kept literally, quotes included: Spark 2.1's CSV escape character is `\`, not `"`. `whitespace`: `" x "` and `"x"` stay different values. `numeric-like`: `01`, `1`, `1.0` are three values. `duplicate-names`: Spark renames `a,a` to `a0,a1` without an error.

**Golden-run slowdown (2026-10-28) and fix.** The first full run slowed down case by case (one case: 969 s) and was stopped after 230 cases. A heap histogram of the long-lived JVM showed Spark's job and SQL listeners retaining per-task metrics of the last 1000 jobs, stages and executions: 15.9M `Long` + 14.6M `Tuple2` (~730 MB), old gen 86% full, GC at ~255% CPU. Fix (test JVMs only, results unaffected): `spark.ui.enabled=false`, `spark.ui.retainedJobs=50`, `spark.ui.retainedStages=50`, `spark.sql.ui.retainedExecutions=50` in surefire's and failsafe's `systemPropertyVariables`, pinned by `SparkTestConfigTest.sessionKeepsLittleUiHistory`. Afterwards the live heap measured 150–210 MB at three points (61 and 86 cases into a check run, 700 cases into the full run), and the same case took 16.9 s.

## Characterization surprises

Legacy behavior that differed from what the plan expected, or a suspicion confirmed:

- **Excel-style input keeps the BOM in the first column name** (`edge/bom-crlf`), so rules read `﻿a=1`.
- **Doubled quotes are not unescaped** (`edge/quoted-values`): `"say ""hi"""` becomes the value `"say ""hi"""`.
- **Duplicate column names are silently renamed** by Spark to `a0`, `a1` (`edge/duplicate-names`); rules then name columns that are not in the file.
- **A failed GridFS validation is only logged** (`StorageIT.failedValidationIsOnlyLogged`): `storeFile` still returns the file, so a corrupt upload would be explored anyway.
- **The two-run nondeterminism check misses most tie-dependent cases**: 42 input-modes flagged, 110 have MCD ties (see Golden corpus run).

- **Large uploads fail after their temporary copy is deleted (confirmed).** `SparkMongoIT.largeFileIsReadAfterItsTemporaryCopyIsDeleted`: for a ~20 MB CSV, `SparkMongo` caches the data, calls `show()` (which reads only the first of several partitions) and deletes `tmp-storage/<name>`; the next action (`count()`) fails with `SparkException` caused by `FileNotFoundException`. Real uploads of this size would fail in the exploration step. Fixed by design in the rewrite (no temp files).
- **CSV with metadata fails for every "decision table" run on `paper-sample.csv`.** Its last column is constant, so there are no rules and `buildStringCSVWithMeta` throws `UnsupportedOperationException: empty.min` (registry #7). The plan's `ExplorationServiceTest` data hit this; the test now uses a table with rules in both modes.
- **Upload names are used as file paths.** `SparkMongoIT.uploadNameIsUsedAsTemporaryFilePath`: a name with a directory part makes the copy fail silently (the exception is only logged) and Spark then reports `Path does not exist`.

## Coverage gaps

**For the owner to accept.** Measured 2026-11-07 with `make legacy-coverage` (fresh `jacoco.exec`, 102 unit + 17 integration tests, 1 skipped). `algorithm*` and `service*`: lines 571/572 (99.8%), branches 160/198 (80.8%). Every package there is at 100% lines except `algorithm.rm` (97.9%). The build now enforces ≥ 95% lines per package for `org.kduda.greedy.algorithm*` and `org.kduda.greedy.service*` (JaCoCo `check` in `verify`); a run with only `SparkTestConfigTest`'s data fails it on exactly those 10 packages.

| Package | Lines | Branches (baseline 2026-10-27) |
|---|---|---|
| `algorithm` | 100% | 80.0% (80.0%) |
| `algorithm.m` | 100% | 83.3% (70.8%) |
| `algorithm.rm` | 97.9% | 80.8% (61.5%) |
| `algorithm.maxcov` | 100% | 85.3% (55.9%) |
| `algorithm.poly` | 100% | 84.4% (59.4%) |
| `algorithm.log` | 100% | 84.4% (59.4%) |
| `algorithm.parser` | 100% | 50.0% (50.0%) |
| `algorithm.util` | 100% | 100% (100%) |
| `service.exploration` | 100% | 100% (0.0%) |
| `service.storage` | 100% | 50.0% (25.0%) |

Tests added in Task 11: `HeuristicChoiceTest.emptyCellsInAConsistentTableAreOrdinaryValues` (null-safe `==` in every heuristic's choose and stop logic; maxCov differs from the others), `StorageIT.deleteByFilenameRemovesEveryFileWithThatNameWhateverItsType`, `deleteByTypeRemovesFilesWithThatTypeMetadata`, `failedValidationIsOnlyLogged`.

Remaining misses, all accepted as unreachable or compiler-generated (checked against the bytecode with `javap`):

| Where | What is missed | Why |
|---|---|---|
| `HeuristicsRM.scala` 52–53 | the `RM == Float.PositiveInfinity` branch and line 53 (`RM = 0.0F`) | `N(T)` counts the source row itself, so it is ≥ 1 and the division never yields infinity. The only missed line in scope. |
| every heuristic, lines 42–53 (one branch per closure: the `for (col <- …)` body and each `filter`/`count` lambda) | the `athrow` after `ifnonnull` in the closure's constructor | Scala 2.11 closures check their enclosing instance for `null`; it never is. All branches of the choose/stop logic itself (score comparison, `==` with and without empty cells, `distinctDecisions > 1`) are covered. |
| every heuristic, line 13–15 (`dts.map { case (key, dt) => … }`); `DecisionTableFactory` 57, 86; `ToStringParser` 17, 22, 48, 52, 85, 94 | the `MatchError` branch of a `case` pattern, or a closure constructor's null check | map entries are never null and always match the tuple pattern |
| `object … extends SparkAware` lines (heuristics 10–12, `DecisionTableFactory` 11) | part of the line, no branch | the trait's `sc`/`sql` accessors are generated on every object but never called |
| `MongoGridFsStorageService` 49, 58, 67, 83, 91, 99 | one branch each | Lombok `@NonNull` parameter checks (`null` argument → `NullPointerException`); no caller passes `null` |
| `algorithm.util.ArrayBufferHelperFactory` | nothing (2/2 lines) | unused by the application; covered by the original unit tests only |

## Proposed registry entries

Added to `PLAN.md`'s registry as #13–#18, marked **(proposed, pending owner approval)**. Each is legacy behavior found in Phase 0 that the new implementation should not reproduce and that no existing entry covers. Without an entry, a golden mismatch on these inputs could not be attributed.

| # | Change | Evidence |
|---|---|---|
| 13 | Drop a UTF-8 BOM at the start of an uploaded CSV | golden `edge/bom-crlf`: first column name `﻿a` in all formats |
| 14 | RFC 4180 quoting: `""` inside a quoted field is one `"` | golden `edge/quoted-values`: `"say ""hi"""` kept literally |
| 15 | Reject empty and header-only files at upload | `StorageIT.emptyUploadIsRejected` (0-byte upload: `StorageException`, HTTP 500, no run); golden `edge/header-only` (empty rule files, csvm crash) |
| 16 | Read files of any size completely | `SparkMongoIT.largeFileIsReadAfterItsTemporaryCopyIsDeleted` |
| 17 | Upload name is display text, never a path | `SparkMongoIT.uploadNameIsUsedAsTemporaryFilePath` |
| 18 | Correct `Content-Type` on rule downloads | `EndToEndGoldenIT` (`application/json` today) |

Considered and not proposed: duplicate column names (Spark renames them to `a0`, `a1`) and the csvm crash in `is` mode are already covered by #11 and #7; the synchronous run inside the HTTP request, open actuator endpoints and the 200 shuffle partitions are architecture, already addressed by the plan's Phase 3 to 5 design rather than by rule output; `MongoGridFsStorageService` only logging a failed GridFS validation and `deleteByFilename` deleting data and rule files alike are legacy storage internals that the new data model replaces.

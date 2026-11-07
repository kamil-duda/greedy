# Phase 0: Safety Net on Legacy Code — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pin down what the 2017 code does today (characterization tests, a coverage baseline and a language-independent golden corpus) before any algorithm or logic code changes.

**Architecture:** Everything runs in Docker (JDK 8 Maven image, MongoDB 4.4) behind `make legacy-*` targets. Algorithm and formatter characterization tests call the Scala objects directly on the shared `GreedySparkInstance` (no Spring context); web and storage tests use Spring's MockMvc and a real MongoDB. A test-scope generator runs the legacy pipeline over a committed input corpus and writes golden outputs plus a manifest; a second run with different Spark parallelism flags nondeterministic cases.

**Tech Stack:** Java 8 test code, JUnit 4.12, AssertJ 2.5.0, Mockito 1.10.19, Spring Boot 1.4.3 test support, Scala 2.11.8 / Spark 2.1.0 (legacy, unchanged), JaCoCo 0.8.12, GNU Make, Docker (`maven:3.8.8-eclipse-temurin-8`, `eclipse-temurin:8-jre`, `mongo:4.4`), Python 3 stdlib (corpus preparation scripts only).

**Spec:** `PLAN.md` — sections "Phase 0: Safety net on legacy code" (checklist), "Testing strategy › Phase 0", "The algorithm as it works today", "Behavior-change registry".

## Global Constraints

- No changes under `src/main/**`. Legacy behavior is frozen; Phase 0 may only change `pom.xml` (build/test plugins and test configuration), `src/test/**`, `Makefile`, `golden/**`, `docs/**`, `PLAN.md`, `AGENTS.md`, `README.md`.
- Build and run only through Docker: `maven:3.8.8-eclipse-temurin-8`, `eclipse-temurin:8-jre`, `mongo:4.4`; Maven cache in the `greedy-legacy-m2` volume. Ports bound to `127.0.0.1`.
- Test code matches the existing style: Java 8, tabs, JUnit 4, AssertJ, Mockito 1.x API (`org.mockito.Matchers`, `ArgumentCaptor`).
- Characterization tests pin **current** behavior, bugs included. A test that pins a bug names the registry item in a comment (e.g. `// registry #10`). If a test written from this plan fails against the legacy code, the legacy code is right by definition: record the actual behavior in the test, note the surprise in `docs/plans/phase-0-notes.md`, and flag it in the hand-over.
- Algorithm and formatter characterization tests do not start a Spring context; they use `GreedySparkInstance.sparkSession()`.
- Golden comparison is order-insensitive, as defined by `GoldenFiles.normalize` (Task 6).
- Git (as executed): the agent staged with `git add` and never committed, pushed or pulled; the owner committed. Since 2026-11-07 the rules in `AGENTS.md` apply instead: agents commit on `master-ai` with Conventional Commits, and the owner pushes.
- When a task completes, tick its box(es) in the Phase 0 checklist of `PLAN.md`.

## Review Focus

Inputs the spec implies but the characterization tests do not exercise directly; each is covered by an edge input in Task 8 (run through all heuristics/modes/formats in Task 9) or by an integration test:

1. CSV exported from Excel with a UTF-8 BOM and CRLF line endings: expect the run to succeed and the outputs to show whether the first column name carries the BOM (edge input `bom-crlf.csv`).
2. Non-ASCII text in column names and values (Polish letters): expect it to survive reading and all three output formats unchanged (edge input `unicode.csv`).
3. Quoted values containing commas, quotes and spaces: expect Spark to unquote them and the CSV outputs to show them unquoted (edge input `quoted-values.csv`, end-to-end case 4 in Task 10).
4. A file large enough for Spark to split into several partitions: expect the temporary-file deletion bug to surface (`SparkMongoIT.largeFileIsReadAfterItsTemporaryCopyIsDeleted`, Task 7).
5. Leading and trailing spaces around values: expect `" x "` and `"x"` to be treated as different values (edge input `whitespace.csv`).

---

### Task 1: Legacy harness: Makefile, failsafe fix, Spark test properties

**Files:**
- Create: `Makefile`
- Modify: `pom.xml` (properties; surefire and failsafe `systemPropertyVariables`; failsafe `classesDirectory`)
- Modify: `src/test/resources/spark.properties` (master from a system property)
- Create: `src/test/java/org/kduda/greedy/characterization/SparkTestConfigTest.java`
- Modify: `AGENTS.md` (Commands section points at the make targets)

**Interfaces:**
- Produces: make targets `help` (default), `legacy-mongo`, `legacy-build`, `legacy-up`, `legacy-down`, `legacy-test`, `legacy-test-one T=<Class[#method]>`; every legacy Maven call appends `$(ARGS)`. Maven properties (defaults in brackets) passed to test JVMs as system properties: `legacy.spark.master` [`local[4]`] → `greedy.spark.master`; `legacy.spark.shufflePartitions` [`200`] → `spark.sql.shuffle.partitions`; `legacy.spark.localityWait` [`3s`] → `spark.locality.wait`. Container names: `greedy-legacy-mongo`, `greedy-legacy-app`, `greedy-legacy-mvn`.

- [ ] **Step 1: Write the failing test** `SparkTestConfigTest.sessionUsesMasterAndShufflePartitionsFromSystemProperties`

```java
assertThat(System.getProperty("greedy.spark.master")).isNotNull();
assertThat(GreedySparkInstance.sparkSession().sparkContext().master()).isEqualTo(System.getProperty("greedy.spark.master"));
assertThat(GreedySparkInstance.sparkSession().conf().get("spark.sql.shuffle.partitions")).isEqualTo(System.getProperty("spark.sql.shuffle.partitions"));
```

- [ ] **Step 2: Run it to verify it fails**

Run: `docker run --rm -v "$PWD":/src -v greedy-legacy-m2:/root/.m2 -w /src maven:3.8.8-eclipse-temurin-8 mvn -B test -Dtest=SparkTestConfigTest`
Expected: FAIL, `greedy.spark.master` is null.

- [ ] **Step 3: Pass the properties to test JVMs**

In `pom.xml` add the three `legacy.spark.*` properties and map them in `systemPropertyVariables` of both surefire and failsafe. In `src/test/resources/spark.properties` set `greedy.spark.master=${sys:greedy.spark.master}` (commons-configuration2 interpolation). Main `spark.properties` stays untouched. If `${sys:...}` is not interpolated by commons-configuration2 2.1, keep `local[4]` fixed, change the test to assert `local[4]`, and record in `docs/plans/phase-0-notes.md` that double runs vary only shuffle partitions.

- [ ] **Step 4: Fix failsafe**

Add `<classesDirectory>${project.build.outputDirectory}</classesDirectory>` to the failsafe configuration so `*IT` classes run against `target/classes`, not the Boot-repackaged jar.

- [ ] **Step 5: Create the `Makefile`**

`LEGACY_MVN = docker run --rm --name greedy-legacy-mvn --network container:greedy-legacy-mongo -v "$(CURDIR)":/src -v greedy-legacy-m2:/root/.m2 -w /src maven:3.8.8-eclipse-temurin-8 mvn -B $(ARGS)`. `legacy-mongo` starts `mongo:4.4` as `greedy-legacy-mongo` only if it is not running, publishing `127.0.0.1:27017:27017` and `127.0.0.1:8080:8080`; every target that uses `LEGACY_MVN` depends on `legacy-mongo` (it joins that container's network). `legacy-test` = `$(LEGACY_MVN) verify`; `legacy-test-one` = `$(LEGACY_MVN) test -Dtest='$(T)' -DfailIfNoTests=false`; `legacy-build` = `-DskipTests package`; `legacy-up` runs `eclipse-temurin:8-jre java -jar target/greedy-0.0.1.jar` as `greedy-legacy-app` in the Mongo container's network; `legacy-down` removes both containers. `help` prints targets from `## ` comments. GNU Make 3.81 compatible (no `.ONESHELL`).

- [ ] **Step 6: Verify**

Run: `make legacy-test-one T=SparkTestConfigTest` → PASS.
Run: `make legacy-test-one T=SparkTestConfigTest ARGS='-Dlegacy.spark.master=local[1] -Dlegacy.spark.shufflePartitions=3'` → PASS.
Run: `make legacy-test` → `BUILD SUCCESS`; `target/failsafe-reports` shows `MongoSparkIT` with `Tests run: 2, Failures: 0, Errors: 0`.
Run: `make legacy-up && curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/ && make legacy-down` → `200`.

- [ ] **Step 7: Update docs** — `AGENTS.md` Commands: replace the raw `docker run` lines with the make targets (one line each) and say the recipes live in the `Makefile`. Tick in `PLAN.md`: "`make legacy-up` / `legacy-down`…" and "Fix failsafe…".

- [ ] **Step 8: Stage and hand over**

```bash
git add Makefile pom.xml src/test/resources/spark.properties src/test/java/org/kduda/greedy/characterization/SparkTestConfigTest.java AGENTS.md PLAN.md
```
Suggested message: `Add legacy Makefile, run integration tests against target/classes, make test Spark settings configurable`. Stop until the owner commits.

---

### Task 2: Coverage baseline

**Files:**
- Modify: `pom.xml` (`org.jacoco:jacoco-maven-plugin:0.8.12`)
- Modify: `Makefile` (`legacy-coverage`)
- Create: `docs/plans/phase-0-notes.md`

**Interfaces:**
- Consumes: `legacy-test` (Task 1).
- Produces: `target/site/jacoco/index.html` and `target/site/jacoco/jacoco.csv` after `make legacy-coverage`; `docs/plans/phase-0-notes.md` with sections `## Coverage baseline`, `## Intermittent slowdown`, `## Characterization surprises`, `## Coverage gaps`, `## Proposed registry entries`.

- [ ] **Step 1: Add JaCoCo** — `prepare-agent` with agent includes `org.kduda.greedy.*` (surefire and failsafe both pick up `argLine`, so unit and integration runs append to one `jacoco.exec`); `report` bound to `verify`.
- [ ] **Step 2: Add `legacy-coverage`** — runs `legacy-test`, then prints one line per package from `jacoco.csv`: package, line coverage %, branch coverage %.
- [ ] **Step 3: Run** `make legacy-coverage` → `BUILD SUCCESS`; `grep -c 'HeuristicsM' target/site/jacoco/jacoco.csv` ≥ 1 (Scala classes are instrumented).
- [ ] **Step 4: Record the baseline** in `docs/plans/phase-0-notes.md`: the per-package table, and for `org.kduda.greedy.algorithm*` and `org.kduda.greedy.service*` every class below 95% line coverage with its missed lines. Tick "JaCoCo coverage baseline and gap list" in `PLAN.md`.
- [ ] **Step 5: Stage and hand over** — `git add pom.xml Makefile docs/plans/phase-0-notes.md PLAN.md`; message `Add JaCoCo coverage baseline for the legacy code`. Stop until the owner commits.

---

### Task 3: Intermittent slowdown

Use superpowers:systematic-debugging. Known fact: in one full run `ToStringParserTest` took 667 s; alone it took 20 s; Spark stages sat at 0 running tasks.

**Files:**
- Modify: `docs/plans/phase-0-notes.md` (`## Intermittent slowdown`)
- Possibly modify: `pom.xml` (test-only default of a `legacy.spark.*` property)

- [ ] **Step 1: Measure** — run `make legacy-test` three times; after each, collect `grep -h 'Time elapsed' target/surefire-reports/*.txt target/failsafe-reports/*.txt` into a per-class timing table in the notes.
- [ ] **Step 2: Capture evidence** — if a class runs over 120 s or 5× its median, take two thread dumps 30 s apart while it runs: `docker exec greedy-legacy-mvn sh -c 'jstack $(pgrep -f surefirebooter | head -1)'`; keep the scheduler- and GC-related threads in the notes.
- [ ] **Step 3: Test the locality-wait hypothesis** — run `make legacy-test ARGS='-Dlegacy.spark.localityWait=0'` three times and compare timings.
- [ ] **Step 4: Decide** — if a test-only setting removes the slowdown and all tests still pass, make it the default of its `legacy.spark.*` property (tests and golden runs only; main config untouched) and record why. If the slowdown does not reproduce in six runs, record that; the golden generator's per-case timing log (Task 9) will expose a stall if it happens there.
- [ ] **Step 5: Stage and hand over** — `git add docs/plans/phase-0-notes.md` (+ `pom.xml` if changed) and tick the "Investigate the intermittent slowdown" box; message `Investigate intermittent Spark slowdown in legacy tests`. Stop until the owner commits.

---

### Task 4: Characterization support and decision-table preparation

**Files:**
- Create: `src/test/java/org/kduda/greedy/characterization/support/LegacyTables.java`
- Create: `src/test/java/org/kduda/greedy/characterization/support/LegacyRules.java`
- Create: `src/test/java/org/kduda/greedy/characterization/support/LegacyRulesTest.java`
- Create: `src/test/java/org/kduda/greedy/characterization/DecisionTablePreparationTest.java`

**Interfaces** (`RuleMap` below means `scala.collection.immutable.Map<String, scala.collection.immutable.List<scala.collection.immutable.List<scala.Tuple2<String, String>>>>`, the type the heuristics return and the formatters take; Java has no alias, so write it out):
- Produces `LegacyTables`:
  - `static Dataset<Row> table(String header, String... rows)` — nullable string columns; an empty field becomes `null`, as Spark 2.1's CSV reader does.
  - `static scala.collection.immutable.Map<String, Dataset<Row>> asDecisionTable(Dataset<Row> t)` — exactly the service's `dt` path: `createMapOf(removeInconsistenciesMCD(new Dataset[]{t}))`.
  - `static scala.collection.immutable.Map<String, Dataset<Row>> asInformationSystem(Dataset<Row> t)` — the `is` path: `createMapOf(removeInconsistenciesMCD(extractDecisionTables(t)))`.
  - `static List<String> rows(Dataset<Row> df)` — `Row.toString()` of every row, sorted, e.g. `[0,n]`, `[null,y]`.
- Produces `LegacyRules`:
  - `static List<String> render(RuleMap rules)` — every rule as `B=0 & A=0 => D=y [1]` (conditions in stored order, then decision, then support), all decision attributes together, sorted.
  - `static RuleMap ruleSet(String... rules)` — the inverse: parses the same notation into the legacy layout `(support, n) :: (decisionAttr, value) :: conditions`; decision attributes iterate in first-appearance order (use `scala.collection.immutable.ListMap` or the ≤4-entry `Map`, and prove it in the test).

- [ ] **Step 1: Write the tests** (exact values verified against the legacy code on 2026-10-27)

`LegacyRulesTest.renderThenParseRoundTrips`: `render(ruleSet("B=0 & A=0 => D=y [1]", "A=1 => D=n [2]"))` contains exactly `"A=1 => D=n [2]", "B=0 & A=0 => D=y [1]"`; and `ruleSet("A=0 => B=x [1]", "B=x => A=0 [1]")` iterates keys `B`, then `A`.

`DecisionTablePreparationTest`:
```java
// counts are taken after duplicate rows are dropped: raw n=4,y=3 but distinct y=3,n=2
mostCommonDecisionIsCountedAfterRemovingDuplicateRows:
  rows(asDecisionTable(table("A,D","0,y","0,n","1,n","1,n","1,n","2,y","3,y")).get("D").get())
    containsExactly "[0,y]","[1,n]","[2,y]","[3,y]"
inconsistentGroupTakesTheGloballyMostFrequentDecision:
  table("A,D","0,y","0,n","1,n","2,n","3,y") -> containsExactly "[0,n]","[1,n]","[2,n]","[3,y]"
equalDecisionFrequenciesPickAnUnspecifiedDecision:   // nondeterministic in legacy, registry #2
  table("A,D","0,y","0,n","1,y","2,n") -> size 3, contains "[1,y]","[2,n]", and exactly one of "[0,y]","[0,n]"
inconsistentGroupWithEmptyConditionIsLeftUntouched:  // registry #10
  table("A,D",",y",",n","1,n") -> containsExactly "[1,n]","[null,n]","[null,y]"
informationSystemModeBuildsOneTablePerColumnWithThatColumnLast:
  asInformationSystem(table("A,B,C","0,1,2","1,1,2")) has keys A,B,C; the "A" table's columns are [B, C, A]
```

- [ ] **Step 2: Run** `make legacy-test-one T='LegacyRulesTest,DecisionTablePreparationTest'` → FAIL to compile (helpers missing).
- [ ] **Step 3: Implement** `LegacyTables` and `LegacyRules` with the signatures above.
- [ ] **Step 4: Run** the same command → PASS.
- [ ] **Step 5: Stage and hand over** — `git add src/test/java/org/kduda/greedy/characterization`; tick "Characterization tests for table extraction, de-duplication, MCD"; message `Characterize decision-table preparation and inconsistency removal`. Stop until the owner commits.

---

### Task 5: Heuristic choice characterization

**Files:**
- Create: `src/test/java/org/kduda/greedy/characterization/HeuristicChoiceTest.java`

**Interfaces:**
- Consumes: `LegacyTables.table`, `LegacyTables.asDecisionTable`, `LegacyRules.render` (Task 4); `HeuristicsM|RM|MaxCov|Poly|Log.calculateDecisionRules`.

Tables (header first, then rows):

| Name | Header | Rows |
|---|---|---|
| T1 | `A,B,D` | `0,0,y` `0,1,y` `0,2,y` `0,3,n` `0,4,n` `1,0,n` |
| T1′ (same data, columns swapped) | `B,A,D` | `0,0,y` `1,0,y` `2,0,y` `3,0,n` `4,0,n` `0,1,n` |
| T2 (XOR) | `A,B,D` | `0,0,y` `0,1,n` `1,0,n` `1,1,y` |
| T2′ | `B,A,D` | `0,0,y` `1,0,n` `0,1,n` `1,1,y` |
| T3 | `A,B,D` | `0,0,y` `0,1,y` `1,0,n` `1,1,n` |
| T5 (constant column) | `C,A,D` | `k,0,y` `k,1,n` |
| TN (empty cells) | `A,D` | `,y` `,n` `1,n` |

- [ ] **Step 1: Write the tests** — each asserts `render(H.calculateDecisionRules(asDecisionTable(T)))` with `containsExactly` (values verified against the legacy code on 2026-10-27):

```java
mPrefersFewestOtherDecisionRowsWhileRmPrefersLowestRatio:   // T1
  M:  "A=1 => D=n [1]","B=0 & A=0 => D=y [1]","B=1 => D=y [1]","B=2 => D=y [1]","B=3 => D=n [1]","B=4 => D=n [1]"
  RM: "A=0 & B=0 => D=y [1]","A=1 => D=n [1]","B=1 => D=y [1]","B=2 => D=y [1]","B=3 => D=n [1]","B=4 => D=n [1]"
maxCovAndPolyPreferTheSmallestLossOfSameDecisionRows:      // T1, maxCov and poly identical
  "A=0 & B=0 => D=y [1]","A=0 & B=1 => D=y [1]","A=0 & B=2 => D=y [1]","A=1 => D=n [1]","B=3 => D=n [1]","B=4 => D=n [1]"
logBreaksExactScoreTiesByColumnOrder:                      // T1 vs T1′ (scores 1/log2(2) and 2/log2(4) are both exactly 1.0)
  T1 log:  same list as RM above
  T1′ log: "A=1 => D=n [1]","B=0 & A=0 => D=y [1]","B=1 => D=y [1]","B=2 => D=y [1]","B=3 => D=n [1]","B=4 => D=n [1]"
polyIgnoresColumnOrderWhenScoresDiffer:                    // T1′ poly
  "A=0 & B=0 => D=y [1]","A=1 => D=n [1]","B=1 => D=y [1]","B=2 => D=y [1]","B=3 => D=n [1]","B=4 => D=n [1]"
maxCovRulesDependOnColumnOrder:                            // T1′ maxCov
  "A=0 & B=0 => D=y [1]","A=0 & B=1 => D=y [1]","A=0 & B=2 => D=y [1]","B=0 & A=1 => D=n [1]","B=3 => D=n [1]","B=4 => D=n [1]"
tiesGoToTheEarliestColumnForEveryHeuristic:                // all five heuristics
  T2:  "A=0 & B=0 => D=y [1]","A=0 & B=1 => D=n [1]","A=1 & B=0 => D=n [1]","A=1 & B=1 => D=y [1]"
  T2′: "B=0 & A=0 => D=y [1]","B=0 & A=1 => D=n [1]","B=1 & A=0 => D=n [1]","B=1 & A=1 => D=y [1]"
identicalRulesFromDifferentRowsAreMergedAndSupportIsCounted: // T3, all five
  "A=0 => D=y [2]","A=1 => D=n [2]"
attributesThatDoNotSeparateDecisionsAreNotChosen:          // T5, all five (maxCov's beta > 0 filter)
  "A=0 => D=y [1]","A=1 => D=n [1]"
emptyConditionInInconsistentGroupCrashesEveryHeuristic:    // TN, registry #10
  each heuristic throws java.util.NoSuchElementException with message "next on empty iterator"
```

- [ ] **Step 2: Run** `make legacy-test-one T=HeuristicChoiceTest` → PASS (characterization of existing code; a failure means the legacy result differs from this plan: follow the Global Constraints rule).
- [ ] **Step 3: Stage and hand over** — `git add src/test/java/org/kduda/greedy/characterization/HeuristicChoiceTest.java PLAN.md`; tick "Characterization tests for each heuristic"; message `Characterize heuristic attribute choice, ties and support`. Stop until the owner commits.

---

### Task 6: Output formats, file naming and the exploration service

**Files:**
- Create: `src/test/java/org/kduda/greedy/golden/GoldenFiles.java`
- Create: `src/test/java/org/kduda/greedy/golden/GoldenFilesTest.java`
- Create: `src/test/java/org/kduda/greedy/characterization/support/LegacyPipeline.java`
- Create: `src/test/java/org/kduda/greedy/characterization/OutputFormatTest.java`
- Create: `src/test/java/org/kduda/greedy/characterization/ExplorationServiceTest.java`
- Create: `src/test/java/org/kduda/greedy/characterization/LegacyPipelineMatchesServiceTest.java`

**Interfaces:**
- Consumes: Task 4 helpers.
- Produces `GoldenFiles.normalize(String fileName, String content) -> List<String>`: `.rul` → all lines sorted; `-wmetadata.csv` → header line, then rule lines with the leading `index,` removed and sorted, then the six statistics lines verbatim; other `.csv` → lines with the leading `index,` removed, sorted.
- Produces `LegacyPipeline`:
  - `enum Mode { DT("dt"), IS("is") }`, `enum Heuristic { M("m"), RM("rm"), MAX_COV("maxCov"), POLY("poly"), LOG("log") }`, `enum Format { CSV("csv", ".csv"), RSES("rses", ".rul"), CSVM("csvm", "-wmetadata.csv") }`, each with a public `formValue` (and `suffix` for `Format`).
  - `static Dataset<Row> readCsv(File file)` — `new CsvFileSparkCsvReader().read(file, {header=true})`, as `SparkMongo` does.
  - `static RuleMap induce(Dataset<Row> csv, Mode mode, Heuristic heuristic)` — the steps of `DataExplorationService.exploreAndSave` without storage.
  - `static String format(RuleMap rules, Format format)` — the matching `ToStringParser` method.

- [ ] **Step 1: Write the tests**

`GoldenFilesTest`: two `.rul` contents with lines swapped normalize equal; `"0,(A = 1) -> n,1\n1,(A = 0) -> y,1\n"` and `"0,(A = 0) -> y,1\n1,(A = 1) -> n,1\n"` normalize equal; two `-wmetadata.csv` contents differing only in the last statistics line normalize **different**.

`OutputFormatTest` (exact strings; first three verified against the legacy code on 2026-10-27 for an equivalent rule set):
```java
rsesWritesConditionsInStoredOrderAndSupportTwice:
  buildStringRSES(ruleSet("B=0 & A=0 => D=y [1]","A=1 => D=n [1]")) == "(B=0)&(A=0)=>(D=y[1]) 1\n(A=1)=>(D=n[1]) 1\n"
csvConcatenatesConditionsWithoutSeparatorAndOmitsDecisionName:
  buildStringCSV(same) == "0,(B = 0)(A = 0) -> y,2\n1,(A = 1) -> n,1\n"
csvWithMetadataAddsCoverageAndLengthStatistics:
  buildStringCSVWithMeta(same) == "index,rule,coverage,length\n0,(B = 0) & (A = 0) -> (D = y),1,2\n1,(A = 1) -> (D = n),1,1\ncoverage\nmin,avg,max\n1,1.0,1\nlength\nmin,avg,max\n1,1.5,2\n"
indexContinuesAcrossDecisionAttributes:
  buildStringCSV(ruleSet("A=0 => B=x [1]","B=x => A=0 [1]")) == "0,(A = 0) -> x,1\n1,(B = x) -> 0,1\n"
csvDoesNotQuoteValuesContainingCommas:            // registry #6
  buildStringCSV(ruleSet("A=a,b => D=y [1]")) == "0,(A = a,b) -> y,1\n"
csvWithMetadataFailsWhenThereAreNoRules:          // registry #7
  rules = HeuristicsM.calculateDecisionRules(asDecisionTable(table("A,D","0,y","1,y")))
  buildStringCSVWithMeta(rules) throws UnsupportedOperationException "empty.min"
rsesAndCsvAreEmptyWhenThereAreNoRules:
  buildStringRSES(rules) == "" and buildStringCSV(rules) == ""
```

`ExplorationServiceTest` — `new DataExplorationService(sparkMongo, rulesRepository, fileRepository)` with Mockito mocks; `fileRepository.listById("id1")` returns `new FileModel(<name>, "id1")`; `sparkMongo.readCsvById("id1")` returns `table("f1,f2,f3","0,1,1","1,2,1","2,2,1")`; capture `rulesRepository.store(stream, name, eq("id1"), contentType)`:

| Test | name | heuristics, type, output | Expected |
|---|---|---|---|
| `storesCsvUnderTruncatedNameAndTextCsv` | `my.data.v2.csv` | `m`, `dt`, `csv` | name `my-dt-rules-m.csv`, type `text/csv` |
| `storesRsesAsRulFileAndTextPlain` | `paper.csv` | `maxCov`, `is`, `rses` | `paper-is-rules-maxCov.rul`, `text/plain` |
| `storesCsvWithMetadataSuffix` | `paper.csv` | `poly`, `dt`, `csvm` | `paper-dt-rules-poly-wmetadata.csv`, `text/csv` |
| `unknownHeuristicAndOutputFallBackToMAndCsv` | `paper.csv` | `xyz`, `dt`, `xyz` | `paper-dt-rules-m.csv`, `text/csv` |
| `missingTypeIsTreatedAsDecisionTableAndNamedNull` | `paper.csv` | `m`, `null`, `csv` | `paper-null-rules-m.csv` |
| `nameWithoutDotIsKeptWhole` | `data` | `m`, `dt`, `csv` | `data-dt-rules-m.csv` |
| `nameStartingWithDotLosesEverything` | `.hidden.csv` | `m`, `dt`, `csv` | `-dt-rules-m.csv` |
| `missingHeuristicFailsWithNullPointer` | `paper.csv` | `null`, `dt`, `csv` | throws `NullPointerException`, nothing stored |
| `missingOutputFailsWithNullPointer` | `paper.csv` | `m`, `dt`, `null` | throws `NullPointerException`, nothing stored |

In the first three rows also assert the captured stream's content, normalized, equals `format(induce(...))` normalized.

`LegacyPipelineMatchesServiceTest.pipelineProducesWhatTheServiceStores` — for `paper-sample.csv` (from `src/test/resources/files`) × all 5 heuristics × both modes × all 3 formats, and `complex-sample.csv` × all 5 heuristics × `dt` × `csv`: the service's stored content and `LegacyPipeline.format(induce(readCsv(file), …))` normalize equal.

- [ ] **Step 2: Run** `make legacy-test-one T='GoldenFilesTest,OutputFormatTest,ExplorationServiceTest,LegacyPipelineMatchesServiceTest'` → compile FAIL (helpers missing).
- [ ] **Step 3: Implement** `GoldenFiles` and `LegacyPipeline` with the signatures above.
- [ ] **Step 4: Run** the same command → PASS.
- [ ] **Step 5: Stage and hand over** — `git add src/test/java/org/kduda/greedy/golden src/test/java/org/kduda/greedy/characterization PLAN.md`; tick "Characterization tests for formatters (csv, rses, csvm) and file naming"; message `Characterize output formats, file naming and the exploration service`. Stop until the owner commits.

---

### Task 7: Web layer and storage characterization

**Files:**
- Create: `src/test/java/org/kduda/greedy/characterization/WebLayerTest.java`
- Create: `src/test/java/org/kduda/greedy/characterization/StorageIT.java`
- Create: `src/test/java/org/kduda/greedy/characterization/SparkMongoIT.java`

**Interfaces:**
- Consumes: Spring beans `FileRepository`, `RulesRepository`, `ExplorationService`, `MongoGridFsStorageService`, `SparkMongoService`; `make legacy-test` runs `*IT` against MongoDB 4.4.

- [ ] **Step 1: Write the tests**

`WebLayerTest` — `@RunWith(SpringRunner.class) @SpringBootTest @AutoConfigureMockMvc`, `@MockBean` for `FileRepository`, `RulesRepository`, `ExplorationService`:
```java
downloadPutsRawFileNameIntoContentDisposition:  loadResourceById("r1") -> Pair.of("a\"b.rul", ByteArrayResource("x"))
  GET /file/r1 accept */*  -> 200, header Content-Disposition == "attachment; filename=\"a\"b.rul\""
downloadOfUnknownIdIsNotFound:                   loadResourceById("r1") -> null  -> 404
uploadRedirectsToIndexWithFlashMessage:          multipart POST /file (file "t.csv") -> 302, Location "/", flash "message" == "File: t.csv uploaded successfully!"
deletingDataFileAlsoDeletesItsRules:             DELETE /file/d1 -> 302 "/", verify deleteById("d1") and rulesRepository.deleteByParent("d1")
exploringUnknownDataFileIsAServerError:          listById("nope") throws NoSuchElementException -> GET /explore/nope throws NestedServletException caused by NoSuchElementException
postingExploreRunsTheJobBeforeRedirecting:       POST /explore/d1 heuristics=m&type=dt&output=csv -> verify exploreAndSave("d1", model with those values), 302 "/explore/d1"
deletingRulesFileGoesThroughTheDataRepository:   DELETE /explore/d1/r1 -> verify fileRepository.deleteById("r1"), 302 "/explore/d1"
actuatorEnvAndDumpAreOpenWithoutLogin:           GET /env -> 200; GET /dump -> 200
```

`StorageIT` — `@RunWith(SpringRunner.class) @SpringBootTest`, real MongoDB; delete everything created in `@After`:
```java
dataUploadIsStoredAsTextCsvWithTypeData:   fileRepository.store(MockMultipartFile("file","t.csv","text/plain","a,d\n1,y")) ->
                                           listAll() contains name "t.csv"; GridFS file contentType "text/csv", metadata type "data"
emptyUploadIsRejected:                     store(empty "t.csv") throws StorageException "Failed to store empty file t.csv"
rulesAreListedAndDeletedByParent:          rulesRepository.store(stream,"r.rul","p1","text/plain") -> listByParent("p1") has "r.rul"; after deleteByParent("p1") it is empty
missingFileLoadsAsNull:                    fileRepository.loadResourceById(new ObjectId().toString()) == null
```

`SparkMongoIT` — `@RunWith(SpringRunner.class) @SpringBootTest`, real MongoDB:
```java
uploadNameIsUsedAsTemporaryFilePath:              store a CSV named "sub/x.csv" via FileStorageService; readCsvById(id)
                                                  throws org.apache.spark.sql.AnalysisException whose message contains "Path does not exist"
largeFileIsReadAfterItsTemporaryCopyIsDeleted:    store a generated ~20 MB CSV (header "a,b,d", 400000 rows); df = readCsvById(id);
                                                  df.count() is expected to throw (SparkException caused by java.io.FileNotFoundException).
                                                  If it does not, pin what it does instead and record it under "Characterization surprises".
```

- [ ] **Step 2: Run** `make legacy-test-one T=WebLayerTest`, then `make legacy-test-one T='StorageIT,SparkMongoIT'` → PASS.
- [ ] **Step 3: Run** `make legacy-test` → `BUILD SUCCESS` with the new ITs listed in `target/failsafe-reports`.
- [ ] **Step 4: Stage and hand over** — `git add src/test/java/org/kduda/greedy/characterization PLAN.md docs/plans/phase-0-notes.md`; tick "Characterization tests for exploration service, storage, controllers"; message `Characterize web layer, GridFS storage and Spark reading from MongoDB`. Stop until the owner commits.

---

### Task 8: Golden corpus inputs

**Files:**
- Create: `golden/README.md`
- Create: `golden/scripts/prepare_public_datasets.py`, `golden/scripts/generate_random_tables.py`
- Create: `golden/inputs/fixtures/{paper-sample,complex-sample,sample,inconsistencies,tic-tac-toe}.csv` (copies of `src/test/resources/files`)
- Create: `golden/inputs/public/{lenses,zoo,car,monks-1,monks-2,monks-3}.csv`
- Create: `golden/inputs/random/random-001.csv` … `random-100.csv`
- Create: `golden/inputs/edge/*.csv` (table below)

**Interfaces:**
- Produces: input ids = path under `golden/inputs` without `.csv` (e.g. `public/lenses`); every file has a header row and the decision attribute last.

- [ ] **Step 1: Public datasets** — download the UCI Machine Learning Repository archives (lenses, zoo, car evaluation, MONK's problems) into a new empty directory outside the repo; write `prepare_public_datasets.py --source-dir DIR --out golden/inputs/public` (stdlib only, run with `python3 -I`):
  - lenses (`lenses.data`, whitespace-separated): drop the id column; header `age,spectacle-prescription,astigmatic,tear-production-rate,contact-lenses`.
  - zoo (`zoo.data`): drop `animal_name`; header `hair,feathers,eggs,milk,airborne,aquatic,predator,toothed,backbone,breathes,venomous,fins,legs,tail,domestic,catsize,type`.
  - car (`car.data`): header `buying,maint,doors,persons,lug_boot,safety,class`.
  - monks-1/2/3 (`monks-N.train`, space-separated `class a1 … a6 id`): drop id, move class last; header `a1,a2,a3,a4,a5,a6,class`.
  Expected row counts (excluding header): lenses 24, zoo 101, car 1728, monks-1 124, monks-2 169, monks-3 122.
- [ ] **Step 2: Random tables** — `generate_random_tables.py --seed 20261027 --count 100 --out golden/inputs/random`; table *i* uses `random.Random(seed + i)`: 3–40 rows, 1–5 condition columns `c1…cK` plus decision `d`, 2–4 condition values from `0 1 2 3`, 2–3 decision values from `a b c`, and with probability 0.1 one cell left empty. Files `random-001.csv` … `random-100.csv`.
- [ ] **Step 3: Edge inputs** — create byte-exact files:

| File | Content (`\n` line ends unless stated) | Pins |
|---|---|---|
| `empty-file.csv` | 0 bytes | no header at all |
| `header-only.csv` | `a,b,d` | no rows |
| `single-row.csv` | `a,d` / `1,y` | degenerate table |
| `single-decision.csv` | `a,b,d` / `1,2,y` / `2,3,y` | ≤1 decision → no rules |
| `single-condition.csv` | `a,d` / `1,y` / `2,n` / `1,n` | one condition column + MCD |
| `identical-rows.csv` | `a,b,d` / `1,1,y` ×3 | duplicates only |
| `mcd-tie.csv` | `a,d` / `0,y` / `0,n` / `1,y` / `2,n` | MCD tie (registry #2) |
| `empty-cells.csv` | `a,b,d` / `,1,y` / `,1,n` / `2,2,n` | crash (registry #10) |
| `quoted-values.csv` | `a,b,d` / `"x,1",p q,y` / `"say ""hi""",p q,n` / `z,r,n` | Review Focus 3 |
| `dotted-names.csv` | `a.b,c,d` / `1,1,y` / `2,1,n` | registry #11 |
| `duplicate-names.csv` | `a,a,d` / `1,2,y` / `2,1,n` | registry #11 |
| `bom-crlf.csv` | UTF-8 BOM, then `a,d\r\n1,y\r\n2,n\r\n` | Review Focus 1 |
| `unicode.csv` | `wiek,łzawienie,decyzja` / `młody,tak,ą` / `stary,nie,ę` | Review Focus 2 |
| `numeric-like.csv` | `a,d` / `01,y` / `1,n` / `1.0,y` | values are strings |
| `whitespace.csv` | `a,d` / ` x ,y` / `x,n` | Review Focus 5 |

- [ ] **Step 4: README** — `golden/README.md`: purpose; layout (`inputs/`, `outputs/`, `scripts/`); how inputs were made (commands above); attribution table for every public dataset (name, creators, UCI URL and DOI, license CC BY 4.0, download URL and SHA-256 of the archive used); comparison rules (link to `GoldenFiles.normalize`); how to regenerate outputs (`make legacy-golden`, Task 9).
- [ ] **Step 5: Verify** — `ls golden/inputs/random | wc -l` → `100`; `head -1` of each public file matches the headers above; row counts match Step 1.
- [ ] **Step 6: Stage and hand over** — `git add golden PLAN.md`; tick "Golden corpus inputs"; message `Add golden corpus inputs: fixtures, UCI datasets, random and edge tables`. Stop until the owner commits.

---

### Task 9: Golden generator, double run and determinism check

**Files:**
- Create: `src/test/java/org/kduda/greedy/golden/GoldenGenerator.java`
- Create: `src/test/java/org/kduda/greedy/golden/GoldenCompare.java`
- Modify: `Makefile` (`legacy-golden`)
- Create (generated): `golden/outputs/**`, `golden/outputs/manifest.json`, `golden/outputs/nondeterministic.json`
- Modify: `golden/README.md`, `docs/plans/phase-0-notes.md`

**Interfaces:**
- Consumes: `LegacyPipeline`, `GoldenFiles` (Task 6); inputs (Task 8); `legacy.spark.*` properties (Task 1).
- Produces:
  - `GoldenGenerator` (`@Test generate()`, run only via `-Dtest=GoldenGenerator`): system properties `golden.inputs` [`golden/inputs`], `golden.out` [`golden/outputs`], optional `golden.include` (regex on input id). For every input × mode × heuristic: `induce` once, then write `<out>/<input id>/<mode>-<heuristicFormValue><format suffix>` for each format; after each case `GreedySparkInstance.sparkSession().catalog().clearCache()`; log `golden <id> <mode> <heuristic> <millis>ms`.
  - `<out>/manifest.json`: `{"generatedWith": {"sparkMaster", "shufflePartitions", "localityWait", "javaVersion", "date"}, "cases": [{"input", "mode", "heuristic", "millis", "outputs": {"csv": file, "rses": file, "csvm": file} | null, "error": exceptionClass | null, "formatErrors": {"csvm": exceptionClass, …}}]}`; cases sorted by input, then mode (dt, is) and heuristic in the order m, rm, maxCov, poly, log.
  - `GoldenCompare` (`@Test compare()`, run only via `-Dtest=GoldenCompare`), properties `golden.a`, `golden.b`: compares status, error class and normalized outputs case by case; writes `<a>/nondeterministic.json`: `{"comparedWith": <b generatedWith>, "cases": [{"input", "mode", "heuristic", "format", "reason"}]}`; fails only if a case is missing from either run.
  - `make legacy-golden`: run A into `golden/outputs` with `-Dlegacy.spark.master='local[4]' -Dlegacy.spark.shufflePartitions=200` (legacy defaults); run B into `target/golden-run-b` with `'local[1]'` and `1`; then `GoldenCompare` with `golden.a=golden/outputs`, `golden.b=target/golden-run-b`. Each run uses `-Dtest=…` on `$(LEGACY_MVN) test`.

- [ ] **Step 1: Implement** `GoldenGenerator` and `GoldenCompare` with the contracts above, and the `legacy-golden` target.
- [ ] **Step 2: Smoke run** — `make legacy-test-one T=GoldenGenerator ARGS='-Dgolden.include=fixtures/paper-sample -Dgolden.out=target/golden-smoke'` → `target/golden-smoke/fixtures/paper-sample/` has 30 files; `is-m.rul` normalizes equal to `src/test/resources/files/paper-sample-rules.rul`; `manifest.json` has 10 cases, all without `error`.
- [ ] **Step 3: Full run** — `make legacy-golden` in the background (expect roughly 1–2 h for run A and less for run B). Watch the per-case log for stalls (a case over 10 min).
- [ ] **Step 4: Check results** — `manifest.json` has (number of inputs) × 10 cases; record in `docs/plans/phase-0-notes.md`: total and per-run duration, number of error cases by exception class and input group, number and list of nondeterministic cases. Add a "Current outputs" section to `golden/README.md` with those counts and the generation date.
- [ ] **Step 5: Stage and hand over** — `git add src/test/java/org/kduda/greedy/golden Makefile golden docs/plans/phase-0-notes.md PLAN.md`; tick "Golden output generator", "Double-run nondeterminism check" and "Edge-case tests" (the edge inputs have now run through every heuristic, mode and format); message `Generate golden outputs from the legacy pipeline and flag nondeterministic cases`. Stop until the owner commits.

---

### Task 10: End-to-end golden cases through HTTP and MongoDB

**Files:**
- Create: `src/test/java/org/kduda/greedy/characterization/EndToEndGoldenIT.java`

**Interfaces:**
- Consumes: golden outputs (Task 9), `GoldenFiles.normalize` (Task 6), beans `FileRepository`, `RulesRepository`.

- [ ] **Step 1: Write the test** — `@RunWith(SpringRunner.class) @SpringBootTest(webEnvironment = RANDOM_PORT)`, `TestRestTemplate`, real MongoDB. Per case: multipart `POST /file` with the input bytes under the upload name (a `ByteArrayResource` overriding `getFilename()`); find the data id with `fileRepository.listAll()` by name; form `POST /explore/{id}` with `heuristics`, `type`, `output` → 302; `rulesRepository.listByParent(id)` → exactly one file; `GET /file/{ruleId}` with `Accept: */*`. Assert: normalized body equals the normalized golden file; `Content-Disposition` == `attachment; filename="<stored name>"`; `Content-Type` starts with `application/json` (legacy finding, observed 2026-10-27). Delete the data file and its rules afterwards.

| Case | Input | Upload name | heuristics, type, output | Golden file | Stored name |
|---|---|---|---|---|---|
| `paperSampleInformationSystemRses` | `fixtures/paper-sample.csv` | `paper-sample.csv` | `m`, `is`, `rses` | `fixtures/paper-sample/is-m.rul` | `paper-sample-is-rules-m.rul` |
| `complexSampleCsvWithMetadata` | `fixtures/complex-sample.csv` | `complex-sample.csv` | `maxCov`, `dt`, `csvm` | `fixtures/complex-sample/dt-maxCov-wmetadata.csv` | `complex-sample-dt-rules-maxCov-wmetadata.csv` |
| `lensesUnderDottedUploadName` | `public/lenses.csv` | `my.data.v2.csv` | `poly`, `dt`, `csv` | `public/lenses/dt-poly.csv` | `my-dt-rules-poly.csv` |
| `quotedValuesInformationSystemRses` | `edge/quoted-values.csv` | `quoted-values.csv` | `log`, `is`, `rses` | `edge/quoted-values/is-log.rul` | `quoted-values-is-rules-log.rul` |

If a chosen case is an error case in `manifest.json`, assert the HTTP flow fails the same way instead (500, and no rules file stored).

- [ ] **Step 2: Run** `make legacy-test-one T=EndToEndGoldenIT` → PASS; then `make legacy-test` → `BUILD SUCCESS`.
- [ ] **Step 3: Stage and hand over** — `git add src/test/java/org/kduda/greedy/characterization/EndToEndGoldenIT.java PLAN.md`; tick "HTTP + MongoDB end-to-end golden cases"; message `Add end-to-end golden cases through HTTP and MongoDB`. Stop until the owner commits.

---

### Task 11: Coverage target

**Files:**
- Modify: `pom.xml` (JaCoCo `check` execution)
- Modify: test classes from Tasks 4–7 (additional tests for gaps)
- Modify: `docs/plans/phase-0-notes.md` (`## Coverage gaps`)

**Interfaces:**
- Consumes: `make legacy-coverage` (Task 2).

- [ ] **Step 1: Measure** — `make legacy-coverage`; list every missed line and branch in `org.kduda.greedy.algorithm*` and `org.kduda.greedy.service*`.
- [ ] **Step 2: Close gaps** — for each, add a test to the characterization class that owns that behavior, or record in `## Coverage gaps` why it is unreachable or unused. Known: `HeuristicsRM`'s `RM = 0.0F` branch is unreachable (the source row always matches its own value, so `N(T(a)) ≥ 1`); `ArrayBufferHelperFactory` is unused by the application.
- [ ] **Step 3: Enforce** — add a JaCoCo `check` (bound to `verify`) with rule element `PACKAGE`, includes `org.kduda.greedy.algorithm*` and `org.kduda.greedy.service*`, `LINE` `COVEREDRATIO` minimum `0.95`. Confirm in the report that every heuristic's branch misses are only the ones listed in `## Coverage gaps`.
- [ ] **Step 4: Run** `make legacy-coverage` → `BUILD SUCCESS` (the check passes).
- [ ] **Step 5: Stage and hand over** — `git add pom.xml src/test docs/plans/phase-0-notes.md PLAN.md`; tick "Coverage target met or gaps accepted by owner" only after the owner accepts the gap list; message `Close legacy coverage gaps and enforce 95% line coverage on algorithm and service code`. Stop until the owner commits.

---

### Task 12: Phase 0 review package

**Files:**
- Modify: `docs/plans/phase-0-notes.md` (summary at the top)
- Modify: `PLAN.md` (registry proposals, ticks)
- Modify: `AGENTS.md` (golden corpus and make targets)

- [ ] **Step 1: Summarize** at the top of the notes: what is now pinned (test counts per area, coverage numbers, corpus size), every characterization surprise, error cases and nondeterministic cases from the golden run, and the slowdown conclusion.
- [ ] **Step 2: Propose registry entries** — for every legacy behavior found in Phase 0 that the new implementation should not reproduce and that is not yet in the registry, add a row to `PLAN.md`'s registry marked **(proposed, pending owner approval)**, and list them under `## Proposed registry entries` in the notes.
- [ ] **Step 3: Update `AGENTS.md`** — the `golden/` corpus (what it is, `make legacy-golden`, order-insensitive comparison) and the `legacy-*` targets including `legacy-coverage` and `legacy-golden`.
- [ ] **Step 4: Verify** — `make legacy-test` and `make legacy-coverage` → `BUILD SUCCESS`; all Phase 0 boxes ticked except "Owner review".
- [ ] **Step 5: Stage and hand over** — `git add docs PLAN.md AGENTS.md`; message `Phase 0 review package`. The owner reviews, approves or rejects each proposed registry entry, then ticks "Owner review". (2026-11-07: reviewed, all six entries approved; the owner decided against a baseline tag.)

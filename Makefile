# Developer commands. `make` lists them.
# build, test-unit, test-one, lock and clean drive the Gradle build of the rewrite through the wrapper (JDK 21).
# legacy-* targets build, test and run the 2017 code in Docker (JDK 8 + MongoDB 4.4); no local JDK needed.
# Pass extra Maven arguments with ARGS, e.g. make legacy-test ARGS='-Dlegacy.spark.master=local[1]'.

GRADLE := ./gradlew
# Written before `make test-one` runs; a test result newer than it shows that some test matched. Results go to one
# file per test class; TEST-Gradle-Test-Run-*.xml is the run's own suite, where the ScalaTest engine reports each suite
# whose tests a pattern starting with * filtered out as one test, so it does not count.
TEST_ONE_STAMP := build/test-one.stamp

LEGACY_MVN_IMAGE   := maven:3.8.8-eclipse-temurin-8
LEGACY_JRE_IMAGE   := eclipse-temurin:8-jre
LEGACY_MONGO_IMAGE := mongo:4.4
LEGACY_MONGO       := greedy-legacy-mongo
LEGACY_APP         := greedy-legacy-app
LEGACY_M2          := greedy-legacy-m2

# Maven runs inside the MongoDB container's network, so tests reach MongoDB on localhost:27017.
# $(value ARGS) passes ARGS to the shell unexpanded, so a regex such as '^public/lenses$' keeps its $.
LEGACY_MVN = docker run --rm --name greedy-legacy-mvn --network container:$(LEGACY_MONGO) \
	-v "$(CURDIR)":/src -v $(LEGACY_M2):/root/.m2 -w /src/legacy $(LEGACY_MVN_IMAGE) mvn -B $(value ARGS)

.DEFAULT_GOAL := help
.PHONY: help build test-unit test-one lock clean
.PHONY: legacy-mongo legacy-build legacy-up legacy-down legacy-test legacy-test-one legacy-coverage legacy-golden

help: ## List available targets
	@grep -E '^[a-zA-Z_-]+:.*## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*## "}; {printf "  %-18s %s\n", $$1, $$2}'

build: ## Gradle build: compile and test every module
	$(GRADLE) build

test-unit: ## Unit tests of every module
	$(GRADLE) test

test-one: ## Tests matching a Gradle --tests pattern: make test-one T='*ModeAndHeuristicSpec' (ScalaTest: by class)
	@[ -n '$(T)' ] || { echo "usage: make test-one T=<pattern>" >&2; exit 2; }
	@mkdir -p $(dir $(TEST_ONE_STAMP)) && touch $(TEST_ONE_STAMP)
	$(GRADLE) test --tests '$(T)' -PtestOne
	@[ -n "$$(find */build/test-results -name 'TEST-*.xml' ! -name 'TEST-Gradle-Test-Run-*' -newer $(TEST_ONE_STAMP) \
		2>/dev/null | head -n 1)" ] || { echo "no test matched $(T)" >&2; exit 1; }

lock: ## Rewrite the dependency lockfiles (gradle.lockfile in every module and in build-logic)
	$(GRADLE) resolveAndLockAll --write-locks
	$(GRADLE) -p build-logic resolveAndLockAll --write-locks

clean: ## Delete the Gradle build outputs and legacy/target
	$(GRADLE) clean
	rm -rf legacy/target

legacy-mongo: ## Start MongoDB 4.4 for the legacy app and tests (no-op if running)
	@if [ "$$(docker inspect -f '{{.State.Running}}' $(LEGACY_MONGO) 2>/dev/null)" != "true" ]; then \
		docker rm -f $(LEGACY_MONGO) >/dev/null 2>&1 || true; \
		docker run -d --name $(LEGACY_MONGO) -p 127.0.0.1:27017:27017 -p 127.0.0.1:8080:8080 $(LEGACY_MONGO_IMAGE) >/dev/null && \
		echo "started $(LEGACY_MONGO)"; \
	fi

legacy-build: legacy-mongo ## Build legacy/target/greedy-0.0.1.jar (tests skipped)
	$(LEGACY_MVN) -DskipTests package

legacy-up: legacy-build ## Run the legacy app on http://localhost:8080
	@docker rm -f $(LEGACY_APP) >/dev/null 2>&1 || true
	docker run -d --name $(LEGACY_APP) --network container:$(LEGACY_MONGO) -v "$(CURDIR)":/src -w /src/legacy \
		$(LEGACY_JRE_IMAGE) java -jar target/greedy-0.0.1.jar
	@echo "waiting for http://localhost:8080 ..."; \
	for i in $$(seq 1 120); do curl -s -o /dev/null http://localhost:8080/ && exit 0; sleep 1; done; \
	echo "legacy app did not start; see: docker logs $(LEGACY_APP)"; exit 1

legacy-down: ## Stop and remove the legacy app and MongoDB containers
	-@docker rm -f $(LEGACY_APP) $(LEGACY_MONGO) >/dev/null 2>&1

legacy-test: legacy-mongo ## Unit + integration tests and the coverage check (about 6 min)
	@rm -f legacy/target/jacoco.exec  # JaCoCo appends; start fresh so coverage reflects this suite run only
	$(LEGACY_MVN) verify

legacy-test-one: legacy-mongo ## One test: make legacy-test-one T=HeuristicsMTest#shouldReturnValidRules (comma-separate classes)
	$(LEGACY_MVN) test -Dtest='$(T)' -DfailIfNoTests=false

legacy-golden: legacy-mongo ## Regenerate golden/outputs: legacy-default run, a local[1] run, determinism report (1-2 h)
	$(LEGACY_MVN) test -Dtest=GoldenGenerator -DfailIfNoTests=false \
		-Dlegacy.spark.master='local[4]' -Dlegacy.spark.shufflePartitions=200
	$(LEGACY_MVN) test -Dtest=GoldenGenerator -DfailIfNoTests=false -Dgolden.out=target/golden-run-b \
		-Dlegacy.spark.master='local[1]' -Dlegacy.spark.shufflePartitions=1
	$(LEGACY_MVN) test -Dtest=GoldenCompare -DfailIfNoTests=false -Dgolden.b=target/golden-run-b
	python3 -I golden/scripts/find_mcd_ties.py --inputs golden/inputs --out golden/outputs/mcd-ties.json

legacy-coverage: legacy-test ## Tests + JaCoCo report (legacy/target/site/jacoco/index.html) and a per-package summary
	@awk -F, 'NR > 1 { bm[$$2] += $$6; bc[$$2] += $$7; lm[$$2] += $$8; lc[$$2] += $$9 } \
	  END { for (p in lm) { l = lm[p] + lc[p]; b = bm[p] + bc[p]; \
	    printf "  %-48s lines %5.1f%%  branches %s\n", p, (l ? 100 * lc[p] / l : 0), (b ? sprintf("%5.1f%%", 100 * bc[p] / b) : "    -") } }' \
	  legacy/target/site/jacoco/jacoco.csv | sort

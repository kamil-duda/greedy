# Developer commands. `make` lists them.
# legacy-* targets build, test and run the 2017 code in Docker (JDK 8 + MongoDB 4.4); no local JDK needed.
# Pass extra Maven arguments with ARGS, e.g. make legacy-test ARGS='-Dlegacy.spark.master=local[1]'.

LEGACY_MVN_IMAGE   := maven:3.8.8-eclipse-temurin-8
LEGACY_JRE_IMAGE   := eclipse-temurin:8-jre
LEGACY_MONGO_IMAGE := mongo:4.4
LEGACY_MONGO       := greedy-legacy-mongo
LEGACY_APP         := greedy-legacy-app
LEGACY_M2          := greedy-legacy-m2

# Maven runs inside the MongoDB container's network, so tests reach MongoDB on localhost:27017.
# $(value ARGS) passes ARGS to the shell unexpanded, so a regex such as '^public/lenses$' keeps its $.
LEGACY_MVN = docker run --rm --name greedy-legacy-mvn --network container:$(LEGACY_MONGO) \
	-v "$(CURDIR)":/src -v $(LEGACY_M2):/root/.m2 -w /src $(LEGACY_MVN_IMAGE) mvn -B $(value ARGS)

.DEFAULT_GOAL := help
.PHONY: help legacy-mongo legacy-build legacy-up legacy-down legacy-test legacy-test-one legacy-coverage legacy-golden \
	spike-env spike-up spike-down spike-clean spike-ui spike-check

help: ## List available targets
	@grep -E '^[a-zA-Z_-]+:.*## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*## "}; {printf "  %-18s %s\n", $$1, $$2}'

legacy-mongo: ## Start MongoDB 4.4 for the legacy app and tests (no-op if running)
	@if [ "$$(docker inspect -f '{{.State.Running}}' $(LEGACY_MONGO) 2>/dev/null)" != "true" ]; then \
		docker rm -f $(LEGACY_MONGO) >/dev/null 2>&1 || true; \
		docker run -d --name $(LEGACY_MONGO) -p 127.0.0.1:27017:27017 -p 127.0.0.1:8080:8080 $(LEGACY_MONGO_IMAGE) >/dev/null && \
		echo "started $(LEGACY_MONGO)"; \
	fi

legacy-build: legacy-mongo ## Build target/greedy-0.0.1.jar (tests skipped)
	$(LEGACY_MVN) -DskipTests package

legacy-up: legacy-build ## Run the legacy app on http://localhost:8080
	@docker rm -f $(LEGACY_APP) >/dev/null 2>&1 || true
	docker run -d --name $(LEGACY_APP) --network container:$(LEGACY_MONGO) -v "$(CURDIR)":/src -w /src \
		$(LEGACY_JRE_IMAGE) java -jar target/greedy-0.0.1.jar
	@echo "waiting for http://localhost:8080 ..."; \
	for i in $$(seq 1 120); do curl -s -o /dev/null http://localhost:8080/ && exit 0; sleep 1; done; \
	echo "legacy app did not start; see: docker logs $(LEGACY_APP)"; exit 1

legacy-down: ## Stop and remove the legacy app and MongoDB containers
	-@docker rm -f $(LEGACY_APP) $(LEGACY_MONGO) >/dev/null 2>&1

legacy-test: legacy-mongo ## Unit + integration tests and the coverage check (about 6 min)
	@rm -f target/jacoco.exec  # JaCoCo appends; start fresh so coverage reflects this suite run only
	$(LEGACY_MVN) verify

legacy-test-one: legacy-mongo ## One test: make legacy-test-one T=HeuristicsMTest#shouldReturnValidRules (comma-separate classes)
	$(LEGACY_MVN) test -Dtest='$(T)' -DfailIfNoTests=false

legacy-golden: legacy-mongo ## Regenerate golden/outputs: legacy-default run, a local[1] run, determinism report (1-2 h)
	$(LEGACY_MVN) test -Dtest=GoldenGenerator -DfailIfNoTests=false -Dgolden.out=golden/outputs \
		-Dlegacy.spark.master='local[4]' -Dlegacy.spark.shufflePartitions=200
	$(LEGACY_MVN) test -Dtest=GoldenGenerator -DfailIfNoTests=false -Dgolden.out=target/golden-run-b \
		-Dlegacy.spark.master='local[1]' -Dlegacy.spark.shufflePartitions=1
	$(LEGACY_MVN) test -Dtest=GoldenCompare -DfailIfNoTests=false -Dgolden.a=golden/outputs -Dgolden.b=target/golden-run-b
	python3 -I golden/scripts/find_mcd_ties.py --inputs golden/inputs --out golden/outputs/mcd-ties.json

legacy-coverage: legacy-test ## Tests + JaCoCo report (target/site/jacoco/index.html) and a per-package summary
	@awk -F, 'NR > 1 { bm[$$2] += $$6; bc[$$2] += $$7; lm[$$2] += $$8; lc[$$2] += $$9 } \
	  END { for (p in lm) { l = lm[p] + lc[p]; b = bm[p] + bc[p]; \
	    printf "  %-48s lines %5.1f%%  branches %s\n", p, (l ? 100 * lc[p] / l : 0), (b ? sprintf("%5.1f%%", 100 * bc[p] / b) : "    -") } }' \
	  target/site/jacoco/jacoco.csv | sort

# Phase 1 spike (throwaway; removed with spike/): Compose stack and checks.
SPIKE_COMPOSE = docker-compose -f spike/compose.yaml -p greedy-spike --env-file spike/.env

spike-env: ## Spike: create spike/.env with random secrets (once)
	@[ -f spike/.env ] || spike/stack/make-env.sh > spike/.env

spike-up: spike-env ## Spike: start PostgreSQL, Garage and the Spark services
	$(SPIKE_COMPOSE) up -d
	spike/stack/init-garage.sh

spike-down: ## Spike: stop the spike stack (keeps its volumes)
	-$(SPIKE_COMPOSE) down

spike-clean: ## Spike: stop the spike stack and delete its volumes
	-$(SPIKE_COMPOSE) down -v

spike-ui: ## Spike: print the service URLs
	@echo "Spark master  http://127.0.0.1:8090"; echo "Spark worker  http://127.0.0.1:8091"; \
	echo "Driver (Connect server) http://127.0.0.1:4040"; echo "History       http://127.0.0.1:18080"; \
	echo "Spark Connect sc://127.0.0.1:15002"; echo "PostgreSQL    127.0.0.1:5432 (db/user spike)"; echo "Garage S3     http://127.0.0.1:3900"

spike-check: ## Spike: check every stack service from the host
	@spike/stack/check.sh

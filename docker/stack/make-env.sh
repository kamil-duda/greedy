#!/usr/bin/env bash
# Prints a .env with a fresh random database password. Used by `make .env`, which never overwrites an existing file.
set -eu
cat <<ENV
GREEDY_DB_PASSWORD=$(openssl rand -hex 16)
SPARK_WORKER_CORES=2
SPARK_WORKER_MEMORY=3g
ENV

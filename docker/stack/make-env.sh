#!/usr/bin/env bash
# Prints a .env with a fresh random database password. Used by `make .env`, which never overwrites an existing file.
set -eu
# Assigned first: set -e does not stop on a failing command substitution inside the here-document
password=$(openssl rand -hex 16)
cat <<ENV
GREEDY_DB_PASSWORD=$password
SPARK_WORKER_CORES=2
SPARK_WORKER_MEMORY=3g
ENV

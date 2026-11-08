#!/usr/bin/env bash
# Idempotent Garage setup for the spike: single-node layout, the spike key, bucket `uploads`.
set -eu
cd "$(dirname "$0")/.."
set -a; . ./.env; set +a
g() { docker-compose -f compose.yaml -p greedy-spike --env-file .env exec -T garage /garage "$@"; }

for i in $(seq 1 30); do g status >/dev/null 2>&1 && break; sleep 1; done
node=$(g node id -q | cut -d@ -f1)
if ! g layout show | rg -q "$node"; then
	g layout assign -z dc1 -c 10G "$node"
	g layout apply --version 1
fi
g key info "$GARAGE_KEY_ID" >/dev/null 2>&1 || g key import --yes -n spike "$GARAGE_KEY_ID" "$GARAGE_SECRET_KEY"
g bucket info uploads >/dev/null 2>&1 || g bucket create uploads
g bucket allow --read --write --owner uploads --key "$GARAGE_KEY_ID"
echo "garage ready: bucket uploads, key $GARAGE_KEY_ID"

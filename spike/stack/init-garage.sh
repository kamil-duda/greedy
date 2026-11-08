#!/usr/bin/env bash
# Idempotent Garage setup for the spike: single-node layout, the spike key, bucket `uploads`.
set -eu
cd "$(dirname "$0")/.."
set -a; . ./.env; set +a
g() { docker-compose -f compose.yaml -p greedy-spike --env-file .env exec -T garage /garage "$@"; }

for i in $(seq 1 30); do g status >/dev/null 2>&1 && break; sleep 1; done
if ! g layout show | rg -q "Current cluster layout version: [1-9]"; then
	g layout assign -z dc1 -c 10G "$(g node id -q | cut -d@ -f1)"
	g layout apply --version 1
fi
g key info "$GARAGE_KEY_ID" >/dev/null 2>&1 || g key import --yes -n spike "$GARAGE_KEY_ID" "$GARAGE_SECRET_KEY"
for bucket in uploads checkpoints; do
	g bucket info "$bucket" >/dev/null 2>&1 || g bucket create "$bucket"
	g bucket allow --read --write --owner "$bucket" --key "$GARAGE_KEY_ID"
done
echo "garage ready: buckets uploads, checkpoints; key $GARAGE_KEY_ID"

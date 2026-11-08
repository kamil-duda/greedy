#!/usr/bin/env bash
# Prints a spike/.env with fresh random secrets. Used once by `make spike-env`.
set -eu
hex() { openssl rand -hex "$1"; }
cat <<ENV
PG_PASSWORD=$(hex 16)
GARAGE_RPC_SECRET=$(hex 32)
GARAGE_ADMIN_TOKEN=$(hex 32)
GARAGE_KEY_ID=GK$(hex 12)
GARAGE_SECRET_KEY=$(hex 32)
ENV

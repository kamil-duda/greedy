#!/usr/bin/env bash
# Phase 1 spike: is every service of the Compose stack up and reachable from the host?
# One line per service; exits non-zero if any check fails.
set -u
cd "$(dirname "$0")/.."
compose=(docker-compose -f compose.yaml -p greedy-spike --env-file .env)
failed=0

report() { # name, command...
	local name=$1; shift
	if "$@" >/dev/null 2>&1; then echo "OK    $name"; else echo "FAIL  $name"; failed=1; fi
}
http_ok() { [ "$(curl -sL -o /dev/null -w '%{http_code}' --max-time 5 "$1")" = "200" ]; }
pg_ready() {
	local bin=/opt/homebrew/opt/libpq@18/bin/pg_isready
	if [ -x "$bin" ]; then "$bin" -h 127.0.0.1 -p 5432 -t 5; else nc -z -w 5 127.0.0.1 5432; fi
}
bucket_exists() { "${compose[@]}" exec -T garage /garage bucket info uploads; }

report "postgres    127.0.0.1:5432 accepts connections" pg_ready
report "garage      bucket 'uploads' exists (S3 on 127.0.0.1:3900)" bucket_exists
report "garage      S3 API answers on 127.0.0.1:3900" nc -z -w 5 127.0.0.1 3900
report "spark-master UI http://127.0.0.1:8090" http_ok http://127.0.0.1:8090
report "spark-worker UI http://127.0.0.1:8091" http_ok http://127.0.0.1:8091
report "spark-connect gRPC 127.0.0.1:15002" nc -z -w 5 127.0.0.1 15002
report "spark-connect driver UI http://127.0.0.1:4040" http_ok http://127.0.0.1:4040
report "spark-history UI http://127.0.0.1:18080" http_ok http://127.0.0.1:18080
exit $failed

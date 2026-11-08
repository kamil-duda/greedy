#!/usr/bin/env bash
# Is every service of the local stack up and configured as expected? One OK/FAIL line per check;
# exits non-zero if any check fails. Run from the repo root (`make stack-check`).
set -u
cd "$(dirname "$0")/../.."
if [ -z "${COMPOSE:-}" ]; then
	if docker compose version >/dev/null 2>&1; then COMPOSE="docker compose"; else COMPOSE="docker-compose"; fi
fi
JAR=/opt/spark/jars/postgresql-42.7.14.jar
failed=0

report() { # name, command...
	local name=$1; shift
	if "$@" >/dev/null 2>&1; then echo "OK    $name"; else echo "FAIL  $name"; failed=1; fi
}
http_ok() { [ "$(curl -sL -o /dev/null -w '%{http_code}' --max-time 5 "$1")" = "200" ]; }
tcp_ok() { nc -z -w 5 127.0.0.1 "$1"; }
pg_ready() { $COMPOSE exec -T postgres pg_isready -U greedy -d greedy; }
has_jar() { $COMPOSE exec -T "$1" test -f "$JAR"; }
# The driver's REST API: both properties must be in the environment of the running Connect application.
driver_conf() {
	local base=http://127.0.0.1:4040/api/v1/applications id
	id=$(curl -s --max-time 5 "$base" | python3 -I -c 'import json,sys; print(json.load(sys.stdin)[0]["id"])') || return 1
	curl -s --max-time 5 "$base/$id/environment" | python3 -I -c '
import json, sys
props = dict(json.load(sys.stdin)["sparkProperties"])
want = {"spark.scheduler.mode": "FAIR", "spark.eventLog.rolling.enabled": "true"}
sys.exit(0 if all(props.get(k) == v for k, v in want.items()) else 1)'
}

report "postgres     pg_isready (user greedy, db greedy)" pg_ready
report "spark-master UI http://127.0.0.1:8090" http_ok http://127.0.0.1:8090
report "spark-worker UI http://127.0.0.1:8091" http_ok http://127.0.0.1:8091
report "spark-connect driver UI http://127.0.0.1:4040" http_ok http://127.0.0.1:4040
report "spark-history UI http://127.0.0.1:18080" http_ok http://127.0.0.1:18080
report "spark-connect gRPC 127.0.0.1:15002" tcp_ok 15002
report "spark-worker  has postgresql-42.7.14.jar" has_jar spark-worker
report "spark-connect has postgresql-42.7.14.jar" has_jar spark-connect
report "spark-connect FAIR scheduler and rolling event logs (driver REST API)" driver_conf
exit $failed

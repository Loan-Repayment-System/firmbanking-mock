#!/usr/bin/env bash
# 해결 방법 하나를 측정한다. k6 서버(로컬 측정이면 개발 PC)에서 실행한다.
#
#   METHOD=named-lock APP_HOST=10.0.1.10 DB_HOST=10.0.1.20 \
#   APP_SSH=ec2-user@10.0.1.10 DB_SSH=ec2-user@10.0.1.20 \
#   MYSQL_ROOT_PASSWORD=... MYSQL_PASSWORD=... ./load-test/run.sh [실행...]
#
# 실행: collection(일괄 집금), payment-baseline(집금 없이 지급·성명조회), payment-main(일괄 집금 중 지급·성명조회).
# 생략하면 세 개를 이 순서로 한다. 실행마다 DB를 새로 만들고 앱을 다시 띄운다.
# APP_SSH·DB_SSH를 비우면 그 서버 명령을 이 PC에서 실행한다.
# 앱 이미지 firmbanking-mock:{METHOD}는 앱 서버에 미리 빌드해 둔다.
# 시간을 줄여 확인할 때는 WARMUP_SECONDS·STAGE_SECONDS·MEASURE_SECONDS를 넘긴다.
#
# 로컬 확인: 거래마다 연결을 여는 부하를 Docker Desktop의 포트 포워딩으로 보내면 연결 시간 초과가 난다.
# DOCKER_NETWORK를 주면 모든 컨테이너를 그 네트워크에 붙이고 포트를 열지 않는다. 호스트는 컨테이너 이름을 쓴다.
#   METHOD=named-lock DOCKER_NETWORK=firmbanking-mock-load APP_HOST=firmbanking-mock-load-app \
#   DB_HOST=firmbanking-mock-load-db MYSQL_PORT=3306 ... ./load-test/run.sh
set -euo pipefail

: "${METHOD:?METHOD가 없다}"
: "${APP_HOST:?APP_HOST가 없다}"
: "${DB_HOST:?DB_HOST가 없다}"
: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD가 없다}"
: "${MYSQL_PASSWORD:?MYSQL_PASSWORD가 없다}"
APP_SSH="${APP_SSH:-}"
DB_SSH="${DB_SSH:-}"
MYSQL_PORT="${MYSQL_PORT:-3307}"
MYSQL_DATABASE="${MYSQL_DATABASE:-firmbanking}"
MYSQL_USER="${MYSQL_USER:-firmbanking}"
DOCKER_NETWORK="${DOCKER_NETWORK:-}"

LOAD_DIR="$(cd "$(dirname "$0")" && pwd)"
RESULT_DIR="$LOAD_DIR/results/$METHOD"
K6_IMAGE=firmbanking-mock-k6
DB_CONTAINER=firmbanking-mock-load-db
APP_CONTAINER=firmbanking-mock-load-app
MONITOR_CONTAINER=firmbanking-mock-load-monitor

if [ -n "$DOCKER_NETWORK" ]; then
  NETWORK_ARGS="--network $DOCKER_NETWORK"
  DB_PORT_ARGS=""
  APP_PORT_ARGS=""
else
  NETWORK_ARGS=""
  DB_PORT_ARGS="-p $MYSQL_PORT:3306"
  APP_PORT_ARGS="-p 9001:9001"
fi

log() {
  echo "[$(date '+%H:%M:%S')] $*"
}

# 대상이 비어 있으면 이 PC에서, 아니면 SSH로 실행한다.
on() {
  local target="$1"
  shift
  if [ -z "$target" ]; then
    bash -c "$*"
  else
    ssh -o BatchMode=yes "$target" "$*"
  fi
}

mysql_client() {
  docker run --rm -i $NETWORK_ARGS mysql:8.4 mysql -h"$DB_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" \
    --default-character-set=utf8mb4 "$@" "$MYSQL_DATABASE" 2>/dev/null
}

# 볼륨 없이 띄워 실행마다 빈 DB에서 시작한다.
reset_db() {
  log "DB 재생성"
  on "$DB_SSH" "docker rm -f $DB_CONTAINER >/dev/null 2>&1 || true; \
    docker run -d --name $DB_CONTAINER $NETWORK_ARGS $DB_PORT_ARGS -e TZ=Asia/Seoul \
      -e MYSQL_ROOT_PASSWORD='$MYSQL_ROOT_PASSWORD' -e MYSQL_DATABASE=$MYSQL_DATABASE \
      -e MYSQL_USER=$MYSQL_USER -e MYSQL_PASSWORD='$MYSQL_PASSWORD' \
      mysql:8.4 --default-time-zone=+09:00 >/dev/null"
  # 이미지의 초기화용 임시 서버는 TCP를 열지 않으므로, TCP로 붙으면 실제 서버가 뜬 것이다.
  for _ in $(seq 60); do
    if echo 'SELECT 1' | mysql_client >/dev/null; then
      return
    fi
    sleep 2
  done
  log "DB가 120초 안에 뜨지 않았다"
  exit 1
}

# 앱이 뜨면서 Flyway가 스키마와 기준 데이터를 넣는다.
start_app() {
  log "앱 기동: firmbanking-mock:$METHOD"
  on "$APP_SSH" "docker rm -f $APP_CONTAINER >/dev/null 2>&1 || true; \
    docker run -d --name $APP_CONTAINER $NETWORK_ARGS $APP_PORT_ARGS -e SPRING_PROFILES_ACTIVE=load \
      -e MYSQL_HOST=$DB_HOST -e MYSQL_PORT=$MYSQL_PORT -e MYSQL_DATABASE=$MYSQL_DATABASE \
      -e MYSQL_USER=$MYSQL_USER -e MYSQL_PASSWORD='$MYSQL_PASSWORD' \
      firmbanking-mock:$METHOD >/dev/null"
  for _ in $(seq 60); do
    if on "$APP_SSH" "docker logs $APP_CONTAINER 2>&1" | grep -q 'Started FirmbankingApplication'; then
      return
    fi
    sleep 2
  done
  log "앱이 120초 안에 뜨지 않았다"
  on "$APP_SSH" "docker logs --tail 50 $APP_CONTAINER"
  exit 1
}

seed() {
  log "시드"
  mysql_client < "$LOAD_DIR/sql/seed.sql"
}

# 커넥션 하나로 1초마다 조회한다. 이 커넥션도 Threads_connected에 1로 잡힌다.
# --unbuffered가 없으면 출력이 버퍼에 남아 있다가 컨테이너를 지울 때 버려진다.
start_monitor() {
  local csv="$1"
  echo 'time,threads_connected,threads_running' > "$csv"
  (
    while true; do
      echo "SELECT DATE_FORMAT(NOW(), '%H:%i:%s'),
        (SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME = 'Threads_connected'),
        (SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME = 'Threads_running');"
      sleep 1
    done | docker run --rm -i --name "$MONITOR_CONTAINER" $NETWORK_ARGS mysql:8.4 mysql -h"$DB_HOST" -P"$MYSQL_PORT" \
      -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" --unbuffered -N -B 2>/dev/null | tr '\t' ',' >> "$csv"
  ) &
  MONITOR_PID=$!
}

stop_monitor() {
  docker rm -f "$MONITOR_CONTAINER" >/dev/null 2>&1 || true
  wait "$MONITOR_PID" 2>/dev/null || true
}

run_k6() {
  local run="$1" script="$2"
  shift 2
  log "k6: $run"
  docker run --rm $NETWORK_ARGS --user "$(id -u):$(id -g)" -v "$LOAD_DIR":/load \
    -e APP_HOST="$APP_HOST" -e RUN="$run" -e RESULT_DIR="/load/results/$METHOD" \
    -e WARMUP_SECONDS -e STAGE_SECONDS -e MEASURE_SECONDS "$@" \
    "$K6_IMAGE" run --quiet "/load/$script"
}

verify() {
  local run="$1"
  local success
  success="$(cat "$RESULT_DIR/$run-transfer-success.txt")"
  log "정확성 검사"
  { echo "SET @k6_transfer_success = $success;"; cat "$LOAD_DIR/sql/verify.sql"; } \
    | mysql_client --table | tee "$RESULT_DIR/$run-verify.txt"
}

measure() {
  local run="$1" script="$2"
  shift 2
  reset_db
  start_app
  seed
  start_monitor "$RESULT_DIR/$run-connections.csv"
  run_k6 "$run" "$script" "$@" || { stop_monitor; exit 1; }
  stop_monitor
  verify "$run"
}

mkdir -p "$RESULT_DIR"
if [ -n "$DOCKER_NETWORK" ]; then
  docker network inspect "$DOCKER_NETWORK" >/dev/null 2>&1 || docker network create "$DOCKER_NETWORK" >/dev/null
fi
docker build -q -t "$K6_IMAGE" "$LOAD_DIR" >/dev/null

runs=("$@")
if [ ${#runs[@]} -eq 0 ]; then
  runs=(collection payment-baseline payment-main)
fi
for run in "${runs[@]}"; do
  case "$run" in
    collection) measure collection collection.js ;;
    payment-baseline) measure payment-baseline collection-with-payment.js -e MODE=baseline ;;
    payment-main) measure payment-main collection-with-payment.js -e MODE=main ;;
    *) log "모르는 실행: $run"; exit 1 ;;
  esac
done
log "결과: $RESULT_DIR"

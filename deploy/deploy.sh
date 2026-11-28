#!/usr/bin/env bash
# 로컬에서 실행: 화면 포함 jar를 만들어 서버에 올리고 다시 시작한다.
#   DEPLOY_HOST=ubuntu@서버IP DEPLOY_KEY=~/.ssh/키파일 [QUICK=1] deploy/deploy.sh [setup]
# setup: 처음 한 번, 서버 설정(setup-server.sh)과 인증키 전달까지 한다. 인증키는 로컬 .env에서 읽는다.
# QUICK=1: 화면(React)만 바뀌고 계산식은 그대로일 때. 시작할 때 지표 재계산을 건너뛰어 느린 서버의 부담을 줄인다.
#          계산식·데이터 처리를 바꾼 배포에서는 쓰지 않는다(저장된 지표가 새 식을 따르지 않는다).
set -euo pipefail

: "${DEPLOY_HOST:?DEPLOY_HOST=ubuntu@서버IP 를 지정하세요}"
: "${DEPLOY_KEY:?DEPLOY_KEY=SSH 키 파일 경로를 지정하세요}"
cd "$(dirname "$0")/.."
SSH=(ssh -i "$DEPLOY_KEY" -o BatchMode=yes "$DEPLOY_HOST")
SCP=(scp -i "$DEPLOY_KEY" -o BatchMode=yes)

if [ "${1:-}" = "setup" ]; then
  echo "== 서버 설정 파일 올리기"
  "${SCP[@]}" deploy/setup-server.sh deploy/fitorder.service "$DEPLOY_HOST:/tmp/"
  SECRETS_ARG=""
  if grep -q '^DATA_GO_KR_SERVICE_KEY=.\+' .env 2>/dev/null; then
    ( umask 077; grep '^DATA_GO_KR_SERVICE_KEY=' .env > /tmp/fitorder-secrets.$$ )
    "${SCP[@]}" /tmp/fitorder-secrets.$$ "$DEPLOY_HOST:/tmp/fitorder-secrets"
    rm -f /tmp/fitorder-secrets.$$
    SECRETS_ARG="/tmp/fitorder-secrets"
  fi
  "${SSH[@]}" "sudo bash /tmp/setup-server.sh $SECRETS_ARG && rm -f /tmp/setup-server.sh /tmp/fitorder.service"
fi

echo "== jar 만들기 (화면 포함)"
./gradlew bootJar -q
JAR="$(ls build/libs/*-SNAPSHOT.jar | head -1)"

echo "== jar 올리기: $JAR"
"${SCP[@]}" "$JAR" "$DEPLOY_HOST:/tmp/fitorder.jar"
"${SSH[@]}" "sudo env QUICK_MODE=${QUICK:-0} bash -s" <<'REMOTE'
set -euo pipefail
[ -f /opt/fitorder/fitorder.jar ] && cp /opt/fitorder/fitorder.jar /opt/fitorder/fitorder.jar.bak
install -o fitorder -g fitorder -m 644 /tmp/fitorder.jar /opt/fitorder/fitorder.jar
rm -f /tmp/fitorder.jar
# QUICK: 이번 시작에만 지표 재계산을 건너뛰도록 systemd 관리자 환경에 잠깐 넣었다가 시작 뒤 지운다
if [ "$QUICK_MODE" = "1" ]; then systemctl set-environment FITORDER_METRICS_RECALC_ON_START=false; fi
systemctl restart fitorder
for i in $(seq 1 40); do
  code="$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8082/ || true)"
  [ "$code" = "200" ] && break
  sleep 2
done
systemctl unset-environment FITORDER_METRICS_RECALC_ON_START || true
echo "핏오더 응답: $code"
[ "$code" = "200" ] || { journalctl -u fitorder -n 30 --no-pager; exit 1; }
systemctl show fitorder -p MemoryCurrent --value | awk '{printf "핏오더 메모리: %.0f MB\n", $1/1048576}'
free -m | awk 'NR==2{print "서버 여유 메모리(available): " $7 " MB"}'
REMOTE

#!/usr/bin/env bash
# 핏오더 서버 최초 설정 (Ubuntu 24.04, root로 실행). 여러 번 실행해도 안전하다(이미 된 것은 건너뜀).
#   sudo bash setup-server.sh [비밀값파일]
# 비밀값파일: DATA_GO_KR_SERVICE_KEY=... 한 줄(공공데이터포털 인증키). 실행 뒤 지운다.
# 하는 일: swap 추가 → PostgreSQL 18 설치·작은 설정 → DB·계정 → 앱 사용자·폴더 → 환경 파일 → systemd 등록
# 하지 않는 일: 다른 서비스 재시작, 패키지 업그레이드, Caddy 변경(add-domain.sh가 따로 한다)
set -euo pipefail

SWAP_GB="${SWAP_GB:-2}"
APP_DIR=/opt/fitorder
ENV_DIR=/etc/fitorder
ENV_FILE="$ENV_DIR/fitorder.env"
SECRETS="${1:-}"
HERE="$(cd "$(dirname "$0")" && pwd)"

log() { printf '\n== %s\n' "$*"; }

# 패키지를 설치하다 다른 서비스(piggy 등)가 자동으로 재시작되지 않게 한다.
export DEBIAN_FRONTEND=noninteractive NEEDRESTART_MODE=l

log "1/6 swap ${SWAP_GB}GB (메모리가 작은 서버의 안전판)"
if swapon --show=NAME --noheadings | grep -qx /swapfile2; then
  echo "이미 있음"
else
  fallocate -l "${SWAP_GB}G" /swapfile2
  chmod 600 /swapfile2
  mkswap /swapfile2 >/dev/null
  swapon /swapfile2
  grep -q '^/swapfile2 ' /etc/fstab || echo '/swapfile2 none swap sw 0 0' >> /etc/fstab
fi

# 파일 캐시가 남아 있는데도 앱 메모리를 먼저 swap으로 내보내면 같은 서버의 다른 서비스가 느려진다(2026-11 piggy 응답이 7초까지 늘어남).
# 파일 캐시를 먼저 비우도록 swappiness를 낮춘다.
echo 'vm.swappiness = 10' > /etc/sysctl.d/99-fitorder-swappiness.conf
sysctl -q -p /etc/sysctl.d/99-fitorder-swappiness.conf

log "2/6 PostgreSQL 18"
if ! dpkg -s postgresql-18 >/dev/null 2>&1; then
  apt-get update -qq
  apt-get install -y -qq postgresql-common curl ca-certificates
  /usr/share/postgresql-common/pgdg/apt.postgresql.org.sh -y >/dev/null
  apt-get install -y -qq postgresql-18
fi
# 메모리를 아끼는 설정(데이터가 작다). io_method=sync는 입출력 보조 프로세스를 만들지 않는다.
CONF=/etc/postgresql/18/main/conf.d/fitorder.conf
mkdir -p "$(dirname "$CONF")"
cat > "$CONF.new" <<'CONF_EOF'
shared_buffers = 16MB
max_connections = 20
work_mem = 2MB
maintenance_work_mem = 16MB
effective_cache_size = 64MB
wal_buffers = 1MB
max_wal_size = 128MB
io_method = sync
CONF_EOF
if ! cmp -s "$CONF.new" "$CONF" 2>/dev/null; then
  mv "$CONF.new" "$CONF"
  systemctl restart postgresql
else
  rm "$CONF.new"
fi
systemctl enable --now postgresql >/dev/null 2>&1

log "3/6 앱 사용자·폴더"
id fitorder >/dev/null 2>&1 || useradd --system --home "$APP_DIR" --shell /usr/sbin/nologin fitorder
mkdir -p "$APP_DIR" "$ENV_DIR"
chown fitorder:fitorder "$APP_DIR"

log "4/6 환경 파일 (비밀값, 권한 600)"
if [ ! -f "$ENV_FILE" ]; then
  DB_PASSWORD="$(openssl rand -hex 16)"
  cat > "$ENV_FILE" <<ENV_EOF
# 핏오더 서버 환경변수. 비밀값이 들어 있다(권한 600, 커밋하지 않는다).
FITORDER_DB_URL=jdbc:postgresql://localhost:5432/fitorder
FITORDER_DB_USER=fitorder
FITORDER_DB_PASSWORD=$DB_PASSWORD
DATA_GO_KR_SERVICE_KEY=
# 시연 계정 비밀번호(기획서에 적는 값). 바꾸면 기획서도 같이 고친다.
FITORDER_USER_PASSWORD=user1234
FITORDER_ADMIN_PASSWORD=admin1234
# 서버 안쪽 주소로만 연다. 바깥에서는 Caddy(HTTPS)로만 들어온다.
SERVER_PORT=8082
SERVER_ADDRESS=127.0.0.1
TZ=Asia/Seoul
ENV_EOF
fi
chmod 600 "$ENV_FILE"
chown root:root "$ENV_FILE"
if [ -n "$SECRETS" ] && [ -f "$SECRETS" ]; then
  KEY_LINE="$(grep '^DATA_GO_KR_SERVICE_KEY=' "$SECRETS" | head -1 || true)"
  if [ -n "$KEY_LINE" ]; then
    grep -v '^DATA_GO_KR_SERVICE_KEY=' "$ENV_FILE" > "$ENV_FILE.new"
    echo "$KEY_LINE" >> "$ENV_FILE.new"
    chmod 600 "$ENV_FILE.new"; mv "$ENV_FILE.new" "$ENV_FILE"
    echo "인증키를 넣었다"
  fi
  shred -u "$SECRETS" 2>/dev/null || rm -f "$SECRETS"
fi

log "5/6 데이터베이스"
DB_PASSWORD="$(grep '^FITORDER_DB_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)"
if ! sudo -u postgres psql -tAc "select 1 from pg_roles where rolname='fitorder'" | grep -q 1; then
  sudo -u postgres psql -qc "create role fitorder login password '$DB_PASSWORD'"
fi
if ! sudo -u postgres psql -tAc "select 1 from pg_database where datname='fitorder'" | grep -q 1; then
  sudo -u postgres createdb -O fitorder fitorder
fi

log "6/6 systemd 서비스 등록"
install -m 644 "$HERE/fitorder.service" /etc/systemd/system/fitorder.service
systemctl daemon-reload
systemctl enable fitorder >/dev/null 2>&1
echo "완료. jar를 올린 뒤 'systemctl restart fitorder' (deploy.sh가 한다)"
free -m

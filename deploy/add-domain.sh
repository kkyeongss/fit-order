#!/usr/bin/env bash
# 서버에서 root로 실행: 기존 Caddy에 도메인을 추가해 HTTPS로 핏오더를 연다.
#   sudo bash add-domain.sh fitorder.duckdns.org
# 전제: 그 도메인의 DNS(A 레코드)가 이 서버 IP를 가리키고 있어야 인증서가 발급된다.
# 안전장치: 기존 설정을 백업하고, 검증(caddy validate)이 통과해야만 적용한다. 이미 있으면 건너뛴다.
set -euo pipefail
DOMAIN="${1:?도메인을 지정하세요. 예: add-domain.sh fitorder.duckdns.org}"
CADDYFILE=/etc/caddy/Caddyfile

if grep -q "^${DOMAIN} {" "$CADDYFILE"; then
  echo "이미 $DOMAIN 설정이 있다. 건너뜀"
  exit 0
fi

BACKUP="$CADDYFILE.bak.$(date +%Y%m%d-%H%M%S)"
cp "$CADDYFILE" "$BACKUP"
cat >> "$CADDYFILE" <<CADDY_EOF

${DOMAIN} {
    reverse_proxy 127.0.0.1:8082
}
CADDY_EOF

if caddy validate --config "$CADDYFILE" --adapter caddyfile >/dev/null 2>&1; then
  systemctl reload caddy
  echo "적용했다. 백업: $BACKUP"
else
  cp "$BACKUP" "$CADDYFILE"
  echo "Caddy 설정 검증에 실패해 되돌렸다(기존 서비스는 그대로)." >&2
  exit 1
fi

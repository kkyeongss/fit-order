# 배포 (jar + systemd + PostgreSQL)

시연 서버는 메모리 1GB(Oracle Cloud Always Free, 다른 서비스 1개와 같이 사용)라 Docker 없이 jar를 systemd로 직접 띄운다.
넉넉한 서버나 기관 내부망으로 옮길 때는 저장소 루트의 `Dockerfile`을 쓴다(README 참고).

## 구성

```
인터넷 → Caddy(80/443, HTTPS 자동) → 127.0.0.1:8082 핏오더(jar) → 127.0.0.1:5432 PostgreSQL 18
```

| 항목 | 위치 |
| --- | --- |
| 앱 | `/opt/fitorder/fitorder.jar` (이전 버전 `fitorder.jar.bak`) |
| 환경 파일(비밀값, 권한 600) | `/etc/fitorder/fitorder.env` |
| 서비스 | `fitorder.service` (`systemctl status fitorder`) |
| 로그 | `journalctl -u fitorder -f` |
| DB | 로컬 PostgreSQL 18, DB·계정 `fitorder` (비밀번호는 환경 파일) |

같은 서버의 다른 서비스를 지키는 장치: 핏오더 메모리 상한(`MemoryHigh=400M`, `MemoryMax=480M`), 메모리가 모자라면 핏오더가 먼저 종료되도록(`OOMScoreAdjust=300`), swap 2GB 추가, 포트는 127.0.0.1에만 열기.
힙은 `-Xmx160m`이지만 프로세스는 약 280MB를 쓴다(2026-11 측정). 서버가 넉넉하면 `fitorder.service`의 `JAVA_OPTS`와 메모리 상한을 키운다.

## 처음 한 번

로컬에서(SSH 키와 서버 주소는 환경변수로, 저장소에 적지 않는다):

```bash
DEPLOY_HOST=ubuntu@서버IP DEPLOY_KEY=SSH키경로 deploy/deploy.sh setup
```

`setup`은 서버 설정(`setup-server.sh`: swap, PostgreSQL 18, DB·계정, 앱 사용자, 환경 파일, systemd)을 하고, 로컬 `.env`의 `DATA_GO_KR_SERVICE_KEY`를 서버 환경 파일에 넣고, jar를 만들어 올려 시작한다. 여러 번 실행해도 안전하다.
설정 중 패키지 설치로 다른 서비스가 재시작되지 않게(`NEEDRESTART_MODE=l`) 했고, 서버의 기존 서비스는 건드리지 않는다.

## 새 버전 올리기

```bash
DEPLOY_HOST=ubuntu@서버IP DEPLOY_KEY=SSH키경로 deploy/deploy.sh
```

화면 포함 jar를 만들어 올리고 재시작한 뒤 응답과 메모리를 확인한다. 문제가 생기면 서버에서
`sudo cp /opt/fitorder/fitorder.jar.bak /opt/fitorder/fitorder.jar && sudo systemctl restart fitorder`.

## 도메인 연결(HTTPS)

1. 도메인의 DNS(A 레코드)를 서버 IP로 연결한다(duckdns.org라면 도메인 설정에서 IP 입력).
2. 서버에서: `sudo bash add-domain.sh 도메인` — 기존 Caddy 설정을 백업하고, 검증에 통과해야만 적용한다. 실패하면 자동으로 되돌린다. 인증서는 Caddy가 알아서 받는다.

## 데이터 적재

서버에서도 화면 '데이터 관리 → 적재'에서 CSV를 올리고, 정원현황과 조달청 시장가는 'API에서 가져오기'를 누르면 된다(관리자 계정).
화면 없이 하려면 서버에서 `curl`로 `/api/admin/uploads`, `/api/admin/sources/headcount/fetch`, `/api/admin/market-prices/fetch`를 부른다(README의 API 표 참고).

## 되돌리기

- 앱: 위 `fitorder.jar.bak` 복원, 또는 `sudo systemctl disable --now fitorder`
- Caddy: `/etc/caddy/Caddyfile.bak.*` 중 가장 최근 것을 복원하고 `sudo systemctl reload caddy`
- swap: `sudo swapoff /swapfile2 && sudo rm /swapfile2` 후 `/etc/fstab`에서 해당 줄 삭제

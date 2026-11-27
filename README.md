# 핏오더 (fit-order)

근무복 주문 데이터로 사이즈별 발주량을 추천하고 ESG 지표(절감액·탄소 감축량)를 보여주는 서비스.
ESG 항목은 모듈 단위로 추가한다. 첫 모듈은 근무복이다. 설계는 `핏오더 개발 기획서.md` 참고.

## 실행

```bash
docker compose up -d        # PostgreSQL 18 (localhost:5432, fitorder/fitorder)
./gradlew bootRun           # DB 스키마는 Flyway가 만든다
./gradlew test
```

시연 계정: `user / user1234` (담당자), `admin / admin1234` (관리자).
운영에서는 `FITORDER_USER_PASSWORD`, `FITORDER_ADMIN_PASSWORD`, `FITORDER_DB_*` 환경변수로 바꾼다.

## 데이터 적재

공공데이터포털 CSV(CP949)를 관리자 API로 올린다.

POST 요청에는 CSRF 토큰이 필요하다. GET으로 `XSRF-TOKEN` 쿠키를 받은 뒤 같은 값을 헤더로 보낸다.

```bash
curl -s -c cj.txt -b cj.txt -u admin:admin1234 http://localhost:8080/api/admin/uploads > /dev/null
TOKEN=$(awk '$6=="XSRF-TOKEN"{print $7}' cj.txt)
curl -c cj.txt -b cj.txt -u admin:admin1234 -H "X-XSRF-TOKEN: $TOKEN" \
     -F source=uniform-order -F baseYear=2026 \
     -F "file=@data/한국체육산업개발주식회사_근무복 주문현황_20260827.csv" \
     http://localhost:8080/api/admin/uploads
```

| source | 데이터 |
| --- | --- |
| `uniform-order` | 근무복 주문현황 |
| `uniform-order-info` | 근무복 주문현황정보 |

- `baseYear`(기준연)를 비우면 파일 안 최대 주문일의 연도를 쓴다.
- 집계는 출처별 최신 기준연 적재만 쓴다(`uniform_order_active` 뷰). 이전 적재는 이력으로 남는다.
- 같은 파일은 두 번 적재되지 않는다. 파일 안의 완전 중복 행은 지우지 않고 `duplicate`로 표시한다.

## 설정으로 바꾸는 것 (`src/main/resources/application.yml`)

- `fitorder.ingestion.sources`: 원천별 인코딩과 CSV 컬럼 매핑. 컬럼명이 바뀌면 여기만 고친다.
- `fitorder.uniform.code-rules`, `item-rules`: 품목 분류 규칙(점퍼 코드 10·11 통합 등).
- `fitorder.uniform.unknown-sizes`: 뜻을 모르는 사이즈 코드. 품질 플래그를 단다.

## 구조

| 패키지 | 역할 |
| --- | --- |
| `common` | 공통 인터페이스(`EsgModule`, `DataIngestion`, `SourceLoader`, `QualityChecker`, `MetricCalculator`, `ExternalSource`), ESG 항목 등록 |
| `ingestion` | 적재 흐름(`LoadService`), CSV 읽기, 적재 이력 |
| `module.uniform` | 근무복: `loader`(적재) / `cleansing`(품질 점검·사이즈 매핑) / `analysis`(지표·추천) / `api` |
| `admin` | 업로드 API, 감사 로그 |

새 ESG 항목은 `EsgModule` 구현 클래스를 `@Component`로 추가하면 시작할 때 `esg_item`에 자동 등록된다.

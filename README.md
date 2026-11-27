# 핏오더 (fit-order)

공공 근무복 주문 데이터로 사이즈별 발주량을 추천하고, 과잉 발주를 줄인 효과를 ESG 지표(절감액·탄소 감축량)로 보여주는 서비스.
ESG 항목은 모듈 단위로 추가하며, 첫 모듈은 근무복이다.

## 기술 스택

Java 21 · Spring Boot 4.1 (Web MVC, Security, Data JPA) · PostgreSQL 18 · Flyway · JUnit 5 · Testcontainers

## 실행

```bash
cp .env.example .env        # 공공데이터포털 인증키 입력
docker compose up -d        # PostgreSQL 18
./gradlew bootRun           # http://localhost:8080
./gradlew test              # 통합 테스트는 Docker 필요
```

| 계정 | 역할 |
| --- | --- |
| `user / user1234` | 담당자(USER) |
| `admin / admin1234` | 관리자(ADMIN) |

비밀번호·인증키·DB 접속 정보는 환경변수(`FITORDER_*`, `DATA_GO_KR_SERVICE_KEY`)로 바꾼다. `.env`는 커밋되지 않는다.

## 데이터

| 원천 코드 | 데이터 | 적재 방식 |
| --- | --- | --- |
| `uniform-order` | 근무복 주문현황 (셔츠·재킷·점퍼·패딩조끼) | CSV 업로드 |
| `uniform-order-info` | 근무복 주문현황정보 (점퍼·바지·조끼) | CSV 업로드 |
| `headcount` | 정원현황 | 공공데이터포털 Open API |

출처: 공공데이터포털, 한국체육산업개발주식회사. 근무복 CSV는 `data/`에 있다.

## 구현된 기능

**적재**
- CSV(CP949) 업로드와 Open API(페이지 단위 전체 수집)
- 같은 파일·같은 응답은 다시 적재하지 않음
- 기준연: 매년 전체 이력이 담긴 파일이 다시 나오므로, 출처별 최신 기준연 적재만 집계에 쓰고 이전 적재는 이력으로 보관
- 적재 이력·감사 로그 기록

**정제**
- 완전 중복 행은 지우지 않고 표시만 함 (집계 기준 `dedup`/`raw` 선택용)
- 수량·날짜 결측, 미분류 품목, 뜻을 모르는 사이즈 코드에 품질 플래그
- 품목 분류 규칙(설정): 점퍼 코드 10·11 통합, 두 파일의 점퍼를 품목명으로 합침
- 사이즈 매핑: 체계가 다른 두 파일의 사이즈를 표준 사이즈에 연결, 매핑 전에는 `코드 (출처)`로 표시
- 정원현황의 넓은 표(직급별 컬럼)를 부서·직군·직급별 행으로 변환

**데이터 원천 관리**
- 원천별 적재 방식·API 주소·인코딩·컬럼 매핑을 DB에서 관리 → CSV가 Open API로 바뀌거나 망분리로 API를 못 쓰게 돼도 설정만 변경
- 미리보기: 원천을 실제로 읽어 컬럼·앞쪽 행·매핑 누락 확인(적재 안 함)

**검증** (실제 데이터로 통합 테스트에 고정)
- 중복 327·331행, 수량 결측 41행, 2020~2025 연평균 1,402벌

## API

모두 로그인 필요. `/api/admin/**`은 ADMIN 전용. POST·PUT은 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 보낸다.

| 메서드 | 경로 | 용도 |
| --- | --- | --- |
| POST | `/api/admin/uploads` | CSV 업로드·적재 (`source`, `file`, 선택 `baseYear`) |
| GET | `/api/admin/uploads` | 적재 이력 |
| GET | `/api/admin/sources` | 데이터 원천 목록 |
| PUT | `/api/admin/sources/{code}` | 원천 설정 수정 |
| POST | `/api/admin/sources/{code}/preview` | 원천 미리보기 |
| POST | `/api/admin/sources/{code}/fetch` | API 원천 적재 |
| GET·PUT | `/api/admin/size-mappings` | 사이즈 매핑 조회·입력 |

## 구조

```
kr.fitorder
├── common           공통 인터페이스, ESG 항목 등록
├── ingestion        적재 흐름, CSV·API 읽기, 데이터 원천 관리, 적재 이력
├── module.uniform   근무복 모듈
│   ├── loader       근무복·정원현황 적재기
│   ├── cleansing    중복·결측·코드 점검, 사이즈 매핑
│   ├── domain       엔티티·저장소
│   └── api          REST API
├── admin            업로드·원천 관리 API, 감사 로그
└── config           보안 설정
```

| 인터페이스 | 역할 |
| --- | --- |
| `EsgModule` | ESG 항목 하나. `@Component`로 추가하면 시작할 때 자동 등록 |
| `SourceLoader` | 원천 하나를 항목 테이블에 저장. 읽는 표준 필드를 선언 |
| `DataIngestion` | 원천을 공통 레코드로 읽기 (`CsvUploadIngestion`, `ApiIngestion`) |
| `QualityChecker` | 중복·결측·코드 점검 |
| `MetricCalculator` | 지표 계산 (구현 예정) |
| `ExternalSource` | 외부 데이터 조회 (구현 예정) |

새 ESG 항목은 `EsgModule`과 `SourceLoader` 구현을 추가하면 기존 코드 수정 없이 붙는다.

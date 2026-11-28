# 핏오더 (fit-order)

공공 근무복 주문 데이터로 사이즈별 발주량을 추천하고, 과잉 발주를 줄인 효과를 ESG 지표(절감액·탄소 감축량)로 보여주는 서비스.
ESG 항목은 모듈 단위로 추가하며, 첫 모듈은 근무복이다.

## 기술 스택

Java 21 · Spring Boot 4.1 (Web MVC, Security, Data JPA) · PostgreSQL 18 · Flyway · Apache POI · JUnit 5 · Testcontainers
React 19 · TypeScript · Vite 8 · Chart.js 4.6 · Vitest (화면, `frontend/`)

## 실행

```bash
cp .env.example .env        # 공공데이터포털 인증키 입력
docker compose up -d        # PostgreSQL 18
./gradlew bootRun           # 서버 http://localhost:8080 (API)
./gradlew test              # 통합 테스트는 Docker 필요
```

화면 개발(서버가 떠 있는 상태에서): `cd frontend && npm install && npm run dev` → http://localhost:5173 (`/api`는 8080으로 전달).
`npm test`(단위 테스트), `npm run format`(prettier). 배포용 jar는 `./gradlew bootJar`가 화면을 빌드해 안에 넣는다(Node 22 이상 필요).
화면은 같은 주소의 `/`에서 열리고(해시 주소 `#/dashboard` 등), 로그인 후 쓸 수 있다.

배포용 이미지(비밀값은 이미지에 넣지 않고 실행할 때 환경변수로):

```bash
docker build -t fit-order .
docker run -d -p 8080:8080 --env-file .env \
  -e FITORDER_DB_URL=jdbc:postgresql://<DB 호스트>:5432/fitorder fit-order
```

이미지에 화면이 포함되어 있다. 기본 JVM 옵션은 작은 서버용(`-Xmx384m`, 실행 시 약 285MB). `JAVA_OPTS`로 바꾼다.

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

외부 데이터(같은 인증키 `DATA_GO_KR_SERVICE_KEY`로 호출, 망분리 환경은 CSV 업로드):

| 데이터 | 쓰는 곳 |
| --- | --- |
| 조달청_나라장터쇼핑몰 품목정보 서비스 | 품목군별 시장가(근무복 계약가격 중앙값), 환경표지제품 가격 |
| 한국환경산업기술원_녹색제품 정보조회 서비스 | 환경표지 인증 근무복 목록(인증기간·기업), 발주 추천의 녹색구매 비교 |

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

**지표** (적재·단가·사이즈 매핑 변경 커밋 직후, 그리고 시작할 때 dedup/raw 두 기준으로 계산해 `esg_metric_value`에 저장)

| 지표 | 계산식 | 기간 |
| --- | --- | --- |
| 연간 주문량 | 품목군별·전체 합계 | 연도 |
| 사이즈 미스매치율 | Σ max(0, 최근 3년 비율 − 이전 3년 비율) | 최근 구간(`2023-2025`) |
| 연간 발주 일수 | 두 데이터를 합친 고유 주문일 수 | 연도 |
| 소량 발주 비율 | 하루 10벌 이하 주문일 ÷ 전체 주문일 | 연도 |
| 1인당 지급량 | 연간 주문량 ÷ 그해 마지막 기준일자 정원 (2021년부터) | 연도 |
| 절감 수량·절감액·탄소 감축량 | 미스매치율 × 연간 주문량 × 개선률 50%, × 단가, × 탄소계수 | 최근 구간 |

- 불완전 연도(첫 해·마지막 해 중 1년치가 안 되는 해: 2017, 2026)는 지표·평균·추천·백테스트에서 뺀다
- 단가 우선순위: 직접 입력 > 조달청 쇼핑몰 시장가(최근 1년 품목군별 중앙값, 표본 5건 이상) > 추정 단가 5.3만원. 출처를 화면·엑셀에 표시
- 시장가 품목군은 규격명 설명 키워드로 나눈다(설정 `fitorder.uniform.market-price.rules`). 쇼핑몰 API 페이지가 불안정해 새 상품이 안 나올 때까지 다시 읽는다

**발주 추천** (규칙 기반, 계수는 `fitorder.uniform.analysis` 설정)
1. 총량 = 직전 2년 평균. 재킷·패딩조끼는 격년 주문이라 같은 홀짝의 가장 최근 해 값
2. 인원 보정 = 총량 × (입력 인원 ÷ 기준 연도 정원). 인원 기본값은 최근 정원
3. 사이즈 배분 = 출처 비중으로 먼저 나누고(점퍼는 두 출처) 출처 안 사이즈 비율로 나눔. 둘 다 최근 3년 3:2:1 가중
4. 안전재고 = 기본 5%, 백테스트 오차 40% 이상인 재킷·바지·패딩조끼 10%
5. 정기 발주 시기 = 4·8월과 과거 수량 비중

결과는 저장하지 않고 화면·엑셀 모두 같은 입력으로 다시 계산한다. 엑셀 발주표에는 행마다 근거(어느 해 평균, 보정 배율, 가중치)가 들어간다.

**검증** (실제 데이터로 통합 테스트에 고정)
- 중복 327·331행, 수량 결측 41행, 2020~2025 연평균 1,402벌, 연평균 발주 28.7일
- 미스매치율 셔츠 16.1%, 점퍼 17.8% (2020~22년 비율로 2023~25년 주문 시)
- 백테스트 2022~2025: 기준선 총량 오차 17.6%(직전 1년)·21.8%(직전 2년 평균) 재현. 추천은 사이즈 오차 42.0%로 기준선(58.4%·44.4%)보다 작고, 격년 규칙으로 재킷 총량 오차 154% → 43%

## 화면

| 화면 | 권한 | 내용 |
| --- | --- | --- |
| 로그인 | 누구나 | 시연 계정 안내, 세션 로그인 |
| 대시보드 | USER | 요약 카드(연평균 주문량·미스매치율·절감액·탄소), 연도별 주문량, 중복 행 비중, 월별 집중도, 1인당 지급량 |
| 사이즈 분석 | USER | 품목군별 사이즈 비중, 이전 3년 vs 최근 3년, 모자랄·남을 구간, 연도별 비중 |
| 발주 추천 | USER | 연도·인원·품목 입력, 품목별 요약, 사이즈별 수량·산정 근거, 정기 발주 시기, 녹색구매 비교, 엑셀, 백테스트 |
| 데이터 관리 | ADMIN | 적재(CSV·API)·이력, 품질 리포트, 단가·시장가·녹색제품, 사이즈 매핑, 원천 설정·미리보기 |
| ESG 항목 | ADMIN | 사용 여부, 원천별 적재 방식, 일괄 전환(망분리), 지표 |

상단의 '중복 제거 / 원자료' 전환은 모든 화면에 적용된다. 불완전 연도는 차트에 *로 표시한다.

## API

`/api/auth/login`(폼 username·password, 성공 시 사용자 JSON) 외에는 로그인 필요, 로그인 전에는 401. `/api/admin/**`과 `PUT /api/esg/**`는 ADMIN 전용. POST·PUT은 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 보낸다.
집계 API는 `basis=dedup|raw`(기본 `dedup`)를 받는다.

| 메서드 | 경로 | 용도 |
| --- | --- | --- |
| POST | `/api/auth/login` · `/api/auth/logout` | 로그인 · 로그아웃 |
| GET | `/api/auth/me` | 현재 사용자·역할 (화면이 처음 호출해 CSRF 쿠키도 받음) |
| GET | `/api/esg/items` | ESG 항목 목록, 원천별 적재 방식, 지표 정의 |
| PUT | `/api/esg/items/{code}` | 사용 여부, 적재 방식 일괄 전환 (`enabled`, `loadMethod`: UPLOAD는 모든 원천, API는 주소가 있는 원천만) |
| GET | `/api/esg/items/{code}/metrics` | 저장된 지표 값 (`basis`, 선택 `period`) |
| GET | `/api/uniform/summary` | 연도별 주문량(중복 구분)·품목군별·발주 일수, 월별 집중도, 1인당 지급량, 연평균 |
| GET | `/api/uniform/sizes` | 품목군별 사이즈 비율(연도별), 두 구간 비교, 미스매치율 (선택 `group`) |
| GET | `/api/uniform/headcount` | 연도별 정원, 최근 정원(추천 기본값)과 직군별 |
| POST | `/api/uniform/recommendation` | 발주 추천 (`year`, `groups`, `headcount`, `basis` 모두 선택) |
| GET | `/api/uniform/recommendation/export` | 발주표 엑셀 (추천과 같은 입력을 쿼리 파라미터로) |
| GET | `/api/uniform/backtest` | 추천 로직 백테스트 |
| GET | `/api/admin/quality` | 품질 리포트 (출처별 중복·플래그·예시 행·미매핑 사이즈) |
| GET·PUT | `/api/admin/prices` | 품목군별 단가 조회(출처 포함)·입력 (`[{group, price}]`) |
| GET | `/api/admin/market-prices` | 조달청 시장가 |
| POST | `/api/admin/market-prices/fetch` · `/upload` | 조달청 API로 갱신 · CSV 업로드 |
| GET | `/api/admin/green-products` | 환경표지 인증 근무복 |
| POST | `/api/admin/green-products/fetch` · `/upload` | 녹색제품 API로 갱신 · CSV 업로드 |
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
├── common           공통 인터페이스, ESG 항목 등록·API, 지표 저장·재계산
├── ingestion        적재 흐름, CSV·API 읽기, 데이터 원천 관리, 적재 이력
├── module.uniform   근무복 모듈
│   ├── loader       근무복·정원현황 적재기
│   ├── cleansing    중복·결측·코드 점검, 사이즈 매핑, 품질 리포트
│   ├── analysis     지표 계산식, 발주 추천, 백테스트, 단가, 화면용 집계
│   ├── metric       지표 계산기(MetricCalculator 구현 8개)
│   ├── price        조달청 시장가·녹색제품(ExternalSource 구현)
│   ├── domain       엔티티·저장소
│   └── api          REST API
├── admin            업로드·원천 관리 API, 감사 로그
└── config           보안 설정, 로그인 API

frontend/            React 화면 (src/pages: 화면별, api.ts: 서버 통신, hooks.tsx, ui.tsx: 공통 부품)
```

| 인터페이스 | 역할 |
| --- | --- |
| `EsgModule` | ESG 항목 하나. `@Component`로 추가하면 시작할 때 자동 등록 |
| `SourceLoader` | 원천 하나를 항목 테이블에 저장. 읽는 표준 필드를 선언 |
| `DataIngestion` | 원천을 공통 레코드로 읽기 (`CsvUploadIngestion`, `ApiIngestion`) |
| `QualityChecker` | 중복·결측·코드 점검 |
| `MetricCalculator` | 지표 하나 계산. `EsgDataChangedEvent`가 오면 `MetricService`가 항목의 지표를 모두 다시 계산 |
| `ExternalSource` | 외부 데이터 조회 (`ProcurementMallSource`, `GreenProductSource`) |

새 ESG 항목은 `EsgModule`과 `SourceLoader` 구현을 추가하면 기존 코드 수정 없이 붙는다.

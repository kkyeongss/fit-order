-- 조달청 나라장터 쇼핑몰 근무복 계약가격을 품목군별로 요약한 값(시장가). 가져올 때마다 통째로 바꾼다.
-- 단가 우선순위: 직접 입력(unit_price) > 시장가(표본 충분할 때) > 추정 단가(설정)
create table market_price (
    item_group       varchar(20)    primary key,
    sample_count     int            not null,
    median_price     numeric(12, 0) not null,
    q1_price         numeric(12, 0) not null,
    q3_price         numeric(12, 0) not null,
    eco_count        int            not null default 0,   -- 환경표지제품(의무구매대상 인증) 수
    eco_median_price numeric(12, 0),
    method           varchar(20)    not null check (method in ('API', 'UPLOAD')),
    period_from      date,
    period_to        date,
    fetched_by       varchar(50)    not null,
    fetched_at       timestamptz    not null default now()
);

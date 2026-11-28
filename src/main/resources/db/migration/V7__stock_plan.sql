-- 현재 재고(품목군·출처·원본 사이즈별). 발주 수량 = 추천 수량 - 재고. 담당자가 입력한다.
create table stock (
    item_group varchar(20) not null,
    source     varchar(50) not null,
    raw_size   varchar(20) not null default '',   -- 사이즈가 없는 행은 ''
    quantity   int         not null check (quantity >= 0),
    updated_by varchar(50) not null,
    updated_at timestamptz not null default now(),
    primary key (item_group, source, raw_size)
);

-- 저장한 발주안(확정 발주). 다음 해 실제 주문과 비교해 효과를 확인한다. 추천 결과의 스냅샷을 JSON으로 둔다.
create table order_plan (
    id        bigserial    primary key,
    plan_year int          not null,
    basis     varchar(10)  not null check (basis in ('DEDUP', 'RAW')),
    headcount int,
    note      varchar(300),
    saved_by  varchar(50)  not null,
    saved_at  timestamptz  not null default now(),
    payload   text         not null
);

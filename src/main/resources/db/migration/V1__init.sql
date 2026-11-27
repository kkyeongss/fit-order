-- ESG 공통 테이블 ---------------------------------------------------------

create table esg_item (
    code        varchar(50)  primary key,
    name        varchar(100) not null,
    category    varchar(1)   not null check (category in ('E', 'S', 'G')),
    enabled     boolean      not null default true,
    load_method varchar(20)  not null,
    created_at  timestamptz  not null default now()
);

create table esg_metric (
    code      varchar(80)  primary key,
    item_code varchar(50)  not null references esg_item (code),
    name      varchar(100) not null,
    unit      varchar(30)  not null
);

create table esg_metric_value (
    id            bigserial     primary key,
    metric_code   varchar(80)   not null references esg_metric (code),
    period        varchar(20)   not null,              -- '2024', '2024-04'
    dimension     varchar(100)  not null default '',   -- 품목 등 세부 구분, 전체는 ''
    basis         varchar(10)   not null check (basis in ('DEDUP', 'RAW')),
    value         numeric(18, 4),
    calculated_at timestamptz   not null default now(),
    unique (metric_code, period, dimension, basis)
);

-- 적재 이력 -----------------------------------------------------------------

create table load_history (
    id              bigserial    primary key,
    item_code       varchar(50)  not null references esg_item (code),
    source          varchar(50)  not null,
    base_year       int,
    file_name       varchar(255),
    file_hash       varchar(64),
    row_count       int          not null default 0,
    duplicate_count int          not null default 0,
    flagged_count   int          not null default 0,
    status          varchar(10)  not null check (status in ('SUCCESS', 'FAILED')),
    message         text,
    active          boolean      not null default false,  -- 출처별로 집계에 쓰는 적재 하나만 true
    loaded_by       varchar(50)  not null,
    loaded_at       timestamptz  not null default now()
);

-- 같은 파일은 한 번만 적재한다.
create unique index ux_load_history_file_hash on load_history (file_hash) where status = 'SUCCESS';
create unique index ux_load_history_active on load_history (source) where active;

-- 근무복 -------------------------------------------------------------------

create table uniform_order (
    id            bigserial    primary key,
    load_id       bigint       not null references load_history (id),
    source        varchar(50)  not null,
    base_year     int          not null,
    line_no       int          not null,
    order_date    date,
    item_code     varchar(20),
    raw_item      varchar(100),
    item          varchar(100),
    item_group    varchar(20)  not null,
    season        varchar(20),
    raw_size      varchar(20),
    quantity      int,
    row_hash      varchar(64)  not null,
    duplicate     boolean      not null default false,
    quality_flags varchar(200) not null default '',
    raw_values    text         not null
);

create index ix_uniform_order_load on uniform_order (load_id);
create index ix_uniform_order_hash on uniform_order (source, base_year, row_hash);

-- 집계는 이 뷰만 쓴다: 출처별 활성 적재(최신 기준연)의 행만 보인다.
create view uniform_order_active as
select o.*
from uniform_order o
join load_history l on l.id = o.load_id
where l.active;

create table size_mapping (
    id            bigserial    primary key,
    source        varchar(50)  not null,
    raw_size      varchar(20)  not null,
    standard_size varchar(20)  not null,
    updated_by    varchar(50)  not null,
    updated_at    timestamptz  not null default now(),
    unique (source, raw_size)
);

create table headcount (
    id         bigserial    primary key,
    load_id    bigint       not null references load_history (id),
    base_date  date         not null,
    department varchar(100) not null,
    job_family varchar(50)  not null,
    grade      varchar(50)  not null,
    quota      int          not null
);

create table unit_price (
    id         bigserial     primary key,
    item       varchar(100)  not null,
    price      numeric(12, 0) not null,
    valid_from date          not null,
    estimated  boolean       not null default false,
    entered_by varchar(50)   not null,
    entered_at timestamptz   not null default now()
);

create table carbon_factor (
    item_group varchar(20)   primary key,
    kg_co2e    numeric(8, 2) not null,
    source     varchar(200)  not null
);

create table audit_log (
    id         bigserial    primary key,
    username   varchar(50)  not null,
    action     varchar(50)  not null,
    target     varchar(200) not null,
    detail     text,
    created_at timestamptz  not null default now()
);

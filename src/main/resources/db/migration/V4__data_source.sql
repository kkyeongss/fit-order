-- 데이터 원천 설정. 처음 시작할 때 application.yml 값으로 채우고, 이후에는 데이터 관리 화면에서 고친다.
-- 인증키 같은 비밀값은 여기 두지 않는다(서버 환경변수).
create table data_source (
    code       varchar(50)  primary key,
    item_code  varchar(50)  not null references esg_item (code),
    name       varchar(100) not null,
    method     varchar(20)  not null check (method in ('UPLOAD', 'API', 'BATCH')),
    url        varchar(500),
    per_page   int          not null default 1000,
    charset    varchar(20)  not null default 'CP949',
    updated_by varchar(50)  not null,
    updated_at timestamptz  not null default now()
);

-- 표준 필드 → 원본 컬럼명
create table data_source_column (
    source_code varchar(50)  not null references data_source (code) on delete cascade,
    field       varchar(50)  not null,
    column_name varchar(100) not null,
    primary key (source_code, field)
);

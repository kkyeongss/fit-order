-- 한국환경산업기술원 녹색제품(환경표지 인증) 근무복 목록. 가져올 때마다 통째로 바꾼다.
create table green_product (
    product_id   varchar(50)  primary key,      -- prodProd(상품ID)
    name         varchar(300) not null,          -- prodPrnm
    category     varchar(100),                   -- 소분류명(ecosClnm)
    model        varchar(300),                   -- prodMdel
    cert_no      varchar(50),                    -- prodRsid 환경표지 인증번호
    cert_from    date,                           -- prodRsdt
    cert_to      date,                           -- prodRedt
    cert_status  varchar(50),                    -- prodRsstnm
    company      varchar(200),                   -- vendVcnm 인증기업명
    g2b_id       varchar(50),                    -- prodG2sn 나라장터 물품식별번호
    item_group   varchar(20),                    -- 이름으로 정한 품목군. 못 정하면 null
    method       varchar(20)  not null check (method in ('API', 'UPLOAD')),
    fetched_by   varchar(50)  not null,
    fetched_at   timestamptz  not null default now()
);

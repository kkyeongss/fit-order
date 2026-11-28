-- 로그인 계정. Spring Security JdbcUserDetailsManager 표준 구조. 처음 시작할 때 설정(application.yml)의 계정으로 채우고,
-- 이후에는 관리자 화면에서 추가·수정한다. 비밀번호는 해시로만 저장한다.
create table users (
    username   varchar(50)  not null primary key,
    password   varchar(500) not null,
    enabled    boolean      not null,
    created_at timestamptz  not null default now()
);

create table authorities (
    username  varchar(50) not null references users (username) on delete cascade,
    authority varchar(50) not null,
    primary key (username, authority)
);

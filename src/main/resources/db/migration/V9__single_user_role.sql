-- 역할은 사용자·관리자 두 가지로 정리한다. 부서장(MANAGER) 역할과 그 시연 계정을 없앤다.
update authorities set authority = 'ROLE_USER'
 where authority = 'ROLE_MANAGER'
   and username not in (select username from authorities where authority = 'ROLE_USER');
delete from authorities where authority = 'ROLE_MANAGER';
delete from users where username = 'manager';

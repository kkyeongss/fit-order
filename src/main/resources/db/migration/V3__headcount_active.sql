create index ix_headcount_load on headcount (load_id);

-- 집계는 이 뷰만 쓴다: 활성 적재의 정원만 보인다.
create view headcount_active as
select h.*
from headcount h
join load_history l on l.id = h.load_id
where l.active;

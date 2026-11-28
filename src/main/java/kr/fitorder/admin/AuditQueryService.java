package kr.fitorder.admin;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 변경 기록(audit_log) 조회. 누가 언제 무엇을(단가, 적재, 계정, 재고 등) 바꿨는지 부서장·관리자가 본다. */
@Service
public class AuditQueryService {

    public record Row(long id, String username, String action, String target, String detail, OffsetDateTime createdAt) {
    }

    private static final int MAX_LIMIT = 1000;

    private final JdbcTemplate jdbc;

    public AuditQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<Row> recent(int limit, String action, String username) {
        int n = Math.max(1, Math.min(limit, MAX_LIMIT));
        StringBuilder sql = new StringBuilder("select id, username, action, target, detail, created_at from audit_log where 1 = 1");
        List<Object> args = new ArrayList<>();
        if (action != null && !action.isBlank()) {
            sql.append(" and action = ?");
            args.add(action.strip());
        }
        if (username != null && !username.isBlank()) {
            sql.append(" and username = ?");
            args.add(username.strip());
        }
        sql.append(" order by id desc limit ?");
        args.add(n);
        return jdbc.query(sql.toString(), (rs, i) -> new Row(rs.getLong("id"), rs.getString("username"),
                rs.getString("action"), rs.getString("target"), rs.getString("detail"),
                rs.getObject("created_at", OffsetDateTime.class)), args.toArray());
    }

    @Transactional(readOnly = true)
    public List<String> actions() {
        return jdbc.queryForList("select distinct action from audit_log order by action", String.class);
    }

    @Transactional(readOnly = true)
    public List<String> usernames() {
        return jdbc.queryForList("select distinct username from audit_log order by username", String.class);
    }
}

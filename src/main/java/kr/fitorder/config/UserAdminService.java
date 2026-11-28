package kr.fitorder.config;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import kr.fitorder.admin.AuditService;
import kr.fitorder.common.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계정 관리. 역할은 담당자(USER)·부서장(MANAGER)·관리자(ADMIN) 중 하나이고, 관리자는 담당자 권한도 가진다.
 * 비밀번호는 해시로만 저장하며, 계정을 지우지 않고 사용 중지로 막는다(변경 기록에 이름이 남도록).
 */
@Service
public class UserAdminService {

    public enum Role {
        USER("담당자"), MANAGER("부서장"), ADMIN("관리자");

        private final String label;

        Role(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** 이 역할이 갖는 권한(ROLE_ 접두). 관리자는 담당자 권한을 함께 갖는다. */
        List<String> authorities() {
            return this == ADMIN ? List.of("ROLE_ADMIN", "ROLE_USER") : List.of("ROLE_" + name());
        }
    }

    public record UserView(String username, Role role, String roleLabel, boolean enabled, OffsetDateTime createdAt) {
    }

    public record Create(String username, String password, Role role) {
    }

    /** 바꿀 값만 보낸다. password는 관리자가 임시 비밀번호로 재설정할 때. */
    public record Update(Role role, Boolean enabled, String password) {
    }

    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9._-]{3,30}$");
    private static final int MIN_PASSWORD = 8;
    private static final int MAX_PASSWORD = 72;

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public UserAdminService(JdbcTemplate jdbc, PasswordEncoder encoder, AuditService audit) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<UserView> list() {
        return jdbc.query("select username, enabled, created_at from users order by username", (rs, i) -> {
            String name = rs.getString("username");
            Role role = roleOf(name);
            return new UserView(name, role, role.label(), rs.getBoolean("enabled"), rs.getObject("created_at", OffsetDateTime.class));
        });
    }

    @Transactional
    public UserView create(Create request, String by) {
        String username = request.username() == null ? "" : request.username().strip();
        if (!USERNAME.matcher(username).matches()) {
            throw new BusinessException("아이디는 영문·숫자·.·_·- 3~30자로 쓰세요.");
        }
        if (request.role() == null) {
            throw new BusinessException("역할(담당자·부서장·관리자)을 고르세요.");
        }
        checkPassword(username, request.password());
        if (exists(username)) {
            throw new BusinessException("이미 있는 아이디입니다: " + username);
        }
        insert(username, request.password(), request.role(), true);
        audit.record(by, "USER_CREATE", username, request.role().label() + " 계정 추가");
        return view(username);
    }

    @Transactional
    public UserView update(String username, Update update, String by) {
        if (!exists(username)) {
            throw new BusinessException("없는 계정입니다: " + username);
        }
        boolean self = username.equals(by);
        if (update.role() != null && update.role() != roleOf(username)) {
            if (self) {
                throw new BusinessException("본인의 역할은 바꿀 수 없습니다. 다른 관리자에게 부탁하세요.");
            }
            guardLastAdmin(username, update.role() != Role.ADMIN, "마지막 관리자의 역할은 바꿀 수 없습니다.");
            jdbc.update("delete from authorities where username = ?", username);
            update.role().authorities().forEach(a -> jdbc.update("insert into authorities (username, authority) values (?, ?)", username, a));
            audit.record(by, "USER_ROLE", username, update.role().label() + "(으)로 변경");
        }
        if (update.enabled() != null && update.enabled() != isEnabled(username)) {
            if (self && !update.enabled()) {
                throw new BusinessException("본인 계정은 사용 중지할 수 없습니다.");
            }
            guardLastAdmin(username, !update.enabled(), "마지막 관리자 계정은 사용 중지할 수 없습니다.");
            jdbc.update("update users set enabled = ? where username = ?", update.enabled(), username);
            audit.record(by, update.enabled() ? "USER_ENABLE" : "USER_DISABLE", username, update.enabled() ? "사용 재개" : "사용 중지");
        }
        if (update.password() != null && !update.password().isBlank()) {
            checkPassword(username, update.password());
            jdbc.update("update users set password = ? where username = ?", encoder.encode(update.password()), username);
            audit.record(by, "USER_PASSWORD_RESET", username, "관리자가 비밀번호 재설정");
        }
        return view(username);
    }

    /** 본인 비밀번호 변경: 현재 비밀번호를 확인한다. */
    @Transactional
    public void changeOwnPassword(String username, String current, String next) {
        String hash = jdbc.query("select password from users where username = ?", rs -> rs.next() ? rs.getString(1) : null, username);
        if (hash == null || current == null || !encoder.matches(current, hash)) {
            throw new BusinessException("현재 비밀번호가 맞지 않습니다.");
        }
        checkPassword(username, next);
        if (encoder.matches(next, hash)) {
            throw new BusinessException("현재 비밀번호와 다른 비밀번호를 쓰세요.");
        }
        jdbc.update("update users set password = ? where username = ?", encoder.encode(next), username);
        audit.record(username, "PASSWORD_CHANGE", username, "본인 비밀번호 변경");
    }

    /** 처음 시작할 때 설정의 계정을 넣는다(이미 있으면 건너뜀). */
    @Transactional
    public boolean seed(String username, String password, Role role) {
        if (exists(username)) {
            return false;
        }
        insert(username, password, role, true);
        return true;
    }

    // ---- 내부 ---------------------------------------------------------------------

    private void insert(String username, String password, Role role, boolean enabled) {
        jdbc.update("insert into users (username, password, enabled) values (?, ?, ?)", username, encoder.encode(password), enabled);
        role.authorities().forEach(a -> jdbc.update("insert into authorities (username, authority) values (?, ?)", username, a));
    }

    private void checkPassword(String username, String password) {
        if (password == null || password.length() < MIN_PASSWORD) {
            throw new BusinessException("비밀번호는 " + MIN_PASSWORD + "자 이상이어야 합니다.");
        }
        if (password.length() > MAX_PASSWORD) {
            throw new BusinessException("비밀번호는 " + MAX_PASSWORD + "자 이하여야 합니다.");
        }
        if (password.equalsIgnoreCase(username)) {
            throw new BusinessException("비밀번호를 아이디와 다르게 쓰세요.");
        }
    }

    private boolean exists(String username) {
        Integer n = jdbc.queryForObject("select count(*) from users where username = ?", Integer.class, username);
        return n != null && n > 0;
    }

    private boolean isEnabled(String username) {
        Boolean b = jdbc.queryForObject("select enabled from users where username = ?", Boolean.class, username);
        return Boolean.TRUE.equals(b);
    }

    private Role roleOf(String username) {
        Set<String> a = Set.copyOf(jdbc.queryForList("select authority from authorities where username = ?", String.class, username));
        return a.contains("ROLE_ADMIN") ? Role.ADMIN : a.contains("ROLE_MANAGER") ? Role.MANAGER : Role.USER;
    }

    /** 사용 중인 관리자가 이 계정 하나뿐이면, 그 계정을 내리는 변경(demote)을 막는다. */
    private void guardLastAdmin(String username, boolean demote, String message) {
        if (!demote || roleOf(username) != Role.ADMIN) {
            return;
        }
        Integer others = jdbc.queryForObject("""
                select count(*) from users u join authorities a on a.username = u.username
                where a.authority = 'ROLE_ADMIN' and u.enabled and u.username <> ?""", Integer.class, username);
        if (others == null || others == 0) {
            throw new BusinessException(message);
        }
    }

    private UserView view(String username) {
        return list().stream().filter(u -> u.username().equals(username)).findFirst().orElseThrow();
    }
}

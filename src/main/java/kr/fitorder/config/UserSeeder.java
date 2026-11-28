package kr.fitorder.config;

import kr.fitorder.config.UserAdminService.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 처음 시작할 때 설정(fitorder.security.users)의 계정을 DB에 넣는다. 이미 있는 계정은 건드리지 않는다
 * (그래서 화면에서 바꾼 비밀번호가 재시작으로 되돌아가지 않는다).
 */
@Component
@Order(0)
public class UserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserSeeder.class);

    private final SecurityProperties properties;
    private final UserAdminService users;

    public UserSeeder(SecurityProperties properties, UserAdminService users) {
        this.properties = properties;
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (SecurityProperties.User u : properties.users()) {
            Role role = Role.valueOf(u.role().toUpperCase());
            if (users.seed(u.username(), u.password(), role)) {
                log.info("계정 등록: {} ({})", u.username(), role.label());
            }
        }
    }
}

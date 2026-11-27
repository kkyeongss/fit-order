package kr.fitorder.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fitorder.security")
public record SecurityProperties(List<User> users) {

    public record User(String username, String password, String role) {
    }

    public SecurityProperties {
        users = users == null ? List.of() : List.copyOf(users);
    }
}

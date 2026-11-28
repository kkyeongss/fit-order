package kr.fitorder.config;

import java.util.List;
import java.util.stream.Collectors;
import kr.fitorder.common.Json;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 화면이 처음 열릴 때 로그인 여부·역할을 묻는다. 로그인 전이면 401(보안 설정). 이 요청으로 XSRF-TOKEN 쿠키도 받는다. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record Me(String username, List<String> roles, boolean admin) {

        static Me of(Authentication auth) {
            List<String> roles = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                    .filter(a -> a.startsWith("ROLE_")).map(a -> a.substring(5)).sorted().toList();
            return new Me(auth.getName(), roles, roles.contains("ADMIN"));
        }

        String toJson() {
            String rolesJson = roles.stream().map(r -> "\"" + r + "\"").collect(Collectors.joining(",", "[", "]"));
            String name = Json.object(java.util.Map.of("username", username));
            return name.substring(0, name.length() - 1) + ",\"roles\":" + rolesJson + ",\"admin\":" + admin + "}";
        }
    }

    @GetMapping("/me")
    public Me me(Authentication auth) {
        return Me.of(auth);
    }
}

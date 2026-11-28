package kr.fitorder.config;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 화면이 처음 열릴 때 로그인 여부·역할을 묻는다. 로그인 전이면 401(보안 설정). 이 요청으로 XSRF-TOKEN 쿠키도 받는다.
 * 비밀번호 변경은 본인만 한다.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /**
     * @param roles   USER, ADMIN
     * @param admin   관리자
     * @param demo    공개 시연 모드(데이터 변경이 막혀 있다)
     */
    public record Me(String username, List<String> roles, boolean admin, boolean demo) {

        static Me of(Authentication auth, boolean demo) {
            List<String> roles = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                    .filter(a -> a.startsWith("ROLE_")).map(a -> a.substring(5)).sorted().toList();
            return new Me(auth.getName(), roles, roles.contains("ADMIN"), demo);
        }
    }

    public record PasswordChange(String current, String next) {
    }

    private final DemoProperties demo;
    private final UserAdminService users;

    public AuthController(DemoProperties demo, UserAdminService users) {
        this.demo = demo;
        this.users = users;
    }

    @GetMapping("/me")
    public Me me(Authentication auth) {
        return Me.of(auth, demo.enabled());
    }

    @PostMapping("/password")
    public void changePassword(@RequestBody PasswordChange request, Authentication auth) {
        users.changeOwnPassword(auth.getName(), request.current(), request.next());
    }
}

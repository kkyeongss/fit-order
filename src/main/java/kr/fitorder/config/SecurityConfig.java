package kr.fitorder.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import kr.fitorder.common.Json;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/** 역할: USER(담당자), ADMIN(관리자). ADMIN은 USER 화면도 쓴다. */
@Configuration
public class SecurityConfig {

    /**
     * 화면(React)은 같은 주소에서 세션 쿠키로 로그인한다.
     * <ul>
     *   <li>POST /api/auth/login (username, password 폼) → 200 + 사용자 JSON, 실패 401</li>
     *   <li>POST /api/auth/logout → 204</li>
     *   <li>로그인 안 한 /api 요청은 로그인 페이지로 보내지 않고 401</li>
     *   <li>CSRF: XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로(로그인·로그아웃 포함)</li>
     * </ul>
     * curl·테스트용으로 HTTP Basic도 받는다.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/esg/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").hasRole("USER")
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        .loginProcessingUrl("/api/auth/login")
                        .successHandler((req, res, auth) -> writeUser(res, auth))
                        .failureHandler((req, res, e) -> writeError(res, HttpStatus.UNAUTHORIZED,
                                "아이디 또는 비밀번호가 맞지 않습니다.")))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        PathPatternRequestMatcher.withDefaults().matcher("/api/**")))
                .httpBasic(Customizer.withDefaults())
                .csrf(csrf -> csrf.spa());
        return http.build();
    }

    static void writeUser(HttpServletResponse res, Authentication auth) throws IOException {
        res.setStatus(HttpStatus.OK.value());
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write(AuthController.Me.of(auth).toJson());
    }

    private static void writeError(HttpServletResponse res, HttpStatus status, String message) throws IOException {
        res.setStatus(status.value());
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write(Json.object(java.util.Map.of("detail", message)));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    InMemoryUserDetailsManager userDetailsService(SecurityProperties properties, PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(properties.users().stream()
                .map(u -> toUser(u, encoder))
                .toList());
    }

    private static UserDetails toUser(SecurityProperties.User u, PasswordEncoder encoder) {
        String[] roles = "ADMIN".equals(u.role()) ? new String[] {"ADMIN", "USER"} : new String[] {u.role()};
        return User.withUsername(u.username()).password(encoder.encode(u.password())).roles(roles).build();
    }
}

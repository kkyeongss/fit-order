package kr.fitorder.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import javax.sql.DataSource;
import kr.fitorder.common.Json;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import tools.jackson.databind.json.JsonMapper;

/**
 * 역할(권한):
 * <ul>
 *   <li>USER(담당자): 조회 + 단가·재고·발주안 입력</li>
 *   <li>MANAGER(부서장): 조회 + 변경 기록 조회. 데이터를 바꾸지 못한다</li>
 *   <li>ADMIN(관리자): 담당자 권한 + 데이터 관리·계정·ESG 항목 설정</li>
 * </ul>
 * 계정은 DB(users, authorities)에 있고 처음 시작할 때 설정(application.yml)으로 채운다.
 */
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
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper mapper, DemoProperties demo) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").authenticated()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/esg/**").hasRole("ADMIN")
                        .requestMatchers("/api/audit/**").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole("USER", "MANAGER", "ADMIN")
                        // 추천 계산은 POST지만 아무것도 저장하지 않는 조회라 부서장도 쓴다
                        .requestMatchers(HttpMethod.POST, "/api/uniform/recommendation").hasAnyRole("USER", "MANAGER", "ADMIN")
                        .requestMatchers("/api/**").hasAnyRole("USER", "ADMIN")
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        .loginProcessingUrl("/api/auth/login")
                        .successHandler((req, res, auth) -> writeUser(res, mapper, auth, demo))
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

    static void writeUser(HttpServletResponse res, JsonMapper mapper, Authentication auth, DemoProperties demo)
            throws IOException {
        res.setStatus(HttpStatus.OK.value());
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write(mapper.writeValueAsString(AuthController.Me.of(auth, demo.enabled())));
    }

    private static void writeError(HttpServletResponse res, HttpStatus status, String message) throws IOException {
        res.setStatus(status.value());
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write(Json.object(Map.of("detail", message)));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** DB(users, authorities)에서 계정을 읽는다. 계정 추가·수정은 UserAdminService가 한다. */
    @Bean
    JdbcUserDetailsManager userDetailsService(DataSource dataSource) {
        return new JdbcUserDetailsManager(dataSource);
    }
}

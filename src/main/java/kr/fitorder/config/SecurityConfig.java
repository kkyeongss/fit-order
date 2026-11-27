package kr.fitorder.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").hasRole("USER")
                        .anyRequest().permitAll())
                .formLogin(Customizer.withDefaults())
                .httpBasic(Customizer.withDefaults())
                .csrf(csrf -> csrf.spa());
        return http.build();
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

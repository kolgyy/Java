package ru.bibank.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import ru.bibank.common.security.CommonSecurityConfig;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CommonSecurityConfig common) throws Exception {
        return common.apply(http)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/internal/**").hasAuthority("ROLE_INTERNAL")
                        .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/**").hasAnyAuthority("ROLE_USER", "ROLE_ADMIN")
                        .anyRequest().denyAll())
                .build();
    }
}

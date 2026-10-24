package ru.bibank.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import ru.bibank.common.web.ApiError;

import java.io.IOException;

/**
 * Базовая конфигурация безопасности для сервлетных сервисов.
 * Каждый сервис строит собственный SecurityFilterChain через {@link #apply(HttpSecurity)}
 * и сам задаёт правила доступа к своим endpoint'ам.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class CommonSecurityConfig {

    private final SecurityProperties properties;
    private final ObjectMapper objectMapper;

    public CommonSecurityConfig(SecurityProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Bean
    public JwtTokenParser jwtTokenParser() {
        return new JwtTokenParser(properties.jwtSecret());
    }

    public HttpSecurity apply(HttpSecurity http) throws Exception {
        return http
                .csrf(c -> c.disable())
                .cors(c -> c.disable())
                .httpBasic(c -> c.disable())
                .formLogin(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new InternalTokenFilter(properties.internalToken()),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenParser()),
                        UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                write(res, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                                        "Требуется аутентификация", req.getRequestURI()))
                        .accessDeniedHandler((req, res, ex) ->
                                write(res, HttpStatus.FORBIDDEN, "FORBIDDEN",
                                        "Недостаточно прав", req.getRequestURI())));
    }

    private void write(jakarta.servlet.http.HttpServletResponse res, HttpStatus status,
                       String code, String message, String path) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(res.getOutputStream(), ApiError.of(status, code, message, path));
    }
}

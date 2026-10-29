package com.nineouting.backend.minutes.config;

import com.nineouting.backend.common.apidocs.ApiDocsPaths;
import com.nineouting.backend.common.security.handler.JsonAccessDeniedHandler;
import com.nineouting.backend.common.security.handler.JsonAuthenticationEntryPoint;
import com.nineouting.backend.common.security.jwt.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * JWT 검증 부품은 security-common 자동 설정이 만든다 (nineouting.security.jwt.jwks-uri).
 */
@Configuration
public class SecurityConfig {

    // /internal/**: ai-service 콜백 등 서비스 간 주소. Gateway가 밖으로 넘기지 않는다 (meeting-service와 같다)
    private static final String[] PUBLIC_PATHS = {"/actuator/health", "/actuator/health/**", "/actuator/prometheus",
            "/internal/**"};

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter,
                                            JsonAuthenticationEntryPoint entryPoint,
                                            JsonAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers(ApiDocsPaths.PUBLIC).permitAll()   // API 문서 (Swagger UI)
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
                .build();
    }
}

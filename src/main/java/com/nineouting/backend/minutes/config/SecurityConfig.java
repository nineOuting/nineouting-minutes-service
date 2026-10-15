package com.nineouting.backend.minutes.config;

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

    private static final String[] PUBLIC_PATHS = {"/actuator/health", "/actuator/health/**", "/actuator/prometheus"};

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
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
                .build();
    }
}

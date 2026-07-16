package com.nursena.payflow.configuration;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {

    private static final String[] PUBLIC_ENDPOINTS = {
        "/api/v1/system/health",
        "/actuator/health",
        "/v3/api-docs/**",
        "/swagger-ui.html",
        "/swagger-ui/**",
        "/api/v1/auth/register",
        "/api/v1/auth/login"
    };

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http
    ) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(PUBLIC_ENDPOINTS)
                .permitAll()
                .requestMatchers(
                    GET,
                    "/api/v1/users/me",
                    "/api/v1/wallets/me",
                    "/api/v1/transactions/me"
                )
                .authenticated()
                .requestMatchers(
                    POST,
                    "/api/v1/wallets",
                    "/api/v1/wallets/me/top-ups",
                    "/api/v1/transfers"
                )
                .authenticated()
                .anyRequest()
                .denyAll()
            )
            .oauth2ResourceServer(resourceServer ->
                resourceServer.jwt(
                    Customizer.withDefaults()
                )
            )
            .httpBasic(httpBasic ->
                httpBasic.disable()
            )
            .formLogin(formLogin ->
                formLogin.disable()
            )
            .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}

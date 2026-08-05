package com.nursena.payflow.configuration;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

import com.nursena.payflow.configuration.security.OperationsAuthorities;
import com.nursena.payflow.configuration.security.PayFlowJwtGrantedAuthoritiesConverter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {

    private static final String[] PUBLIC_ENDPOINTS = {
        "/api/v1/system/health",
        "/actuator/health",
        "/actuator/health/**",
        "/actuator/prometheus",
        "/v3/api-docs/**",
        "/swagger-ui.html",
        "/swagger-ui/**",
        "/api/v1/auth/register",
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/api/v1/auth/logout",
        "/api/v1/auth/email-verification/requests",
        "/api/v1/auth/email-verification/confirm",
        "/api/v1/auth/password-recovery/requests",
        "/api/v1/auth/password-recovery/confirm"
    };

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        JwtAuthenticationConverter jwtAuthenticationConverter
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
                    "/api/v1/operations/**"
                )
                .hasAuthority(
                    OperationsAuthorities.OPERATIONS
                )
                .requestMatchers(
                    GET,
                    "/api/v1/users/me",
                    "/api/v1/wallets/me",
                    "/api/v1/transactions/me"
                )
                .authenticated()
                .requestMatchers(
                    POST,
                    "/api/v1/auth/logout-all",
                    "/api/v1/wallets",
                    "/api/v1/wallets/me/top-ups",
                    "/api/v1/transfers"
                )
                .authenticated()
                .anyRequest()
                .denyAll()
            )
            .oauth2ResourceServer(resourceServer ->
                resourceServer.jwt(jwt ->
                    jwt.jwtAuthenticationConverter(
                        jwtAuthenticationConverter
                    )
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
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter =
            new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(
            new PayFlowJwtGrantedAuthoritiesConverter()
        );

        return converter;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}

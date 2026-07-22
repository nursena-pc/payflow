package com.nursena.payflow.configuration.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class PayFlowJwtGrantedAuthoritiesConverterTest {

    private static final Instant ISSUED_AT =
        Instant.parse("2026-07-22T12:00:00Z");

    private static final Instant EXPIRES_AT =
        Instant.parse("2026-07-22T12:15:00Z");

    private final PayFlowJwtGrantedAuthoritiesConverter
        converter =
        new PayFlowJwtGrantedAuthoritiesConverter();

    @Test
    void shouldMapAdminRoleToOperationsAuthority() {
        assertThat(
            converter.convert(
                jwtWithRole("ADMIN")
            )
        )
            .extracting(
                GrantedAuthority::getAuthority
            )
            .containsExactly(
                OperationsAuthorities.OPERATIONS
            );
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
            "USER",
            "",
            " ",
            "admin",
            "OPERATIONS",
            "UNKNOWN"
        }
    )
    void shouldNotGrantOperationsAuthorityForOtherRoles(
        String role
    ) {
        assertThat(
            converter.convert(
                jwtWithRole(role)
            )
        ).isEmpty();
    }

    @Test
    void shouldNotGrantOperationsAuthorityWhenRoleIsMissing() {
        assertThat(
            converter.convert(
                jwtWithoutRole()
            )
        ).isEmpty();
    }

    @Test
    void shouldNotGrantOperationsAuthorityForNonStringRole() {
        assertThat(
            converter.convert(
                jwtWithRole(
                    List.of("ADMIN")
                )
            )
        ).isEmpty();
    }

    private static Jwt jwtWithRole(
        Object role
    ) {
        return baseJwt()
            .claim("role", role)
            .build();
    }

    private static Jwt jwtWithoutRole() {
        return baseJwt().build();
    }

    private static Jwt.Builder baseJwt() {
        return Jwt
            .withTokenValue("access-token")
            .header("alg", "RS256")
            .subject(
                "10000000-0000-0000-0000-000000000001"
            )
            .issuedAt(ISSUED_AT)
            .expiresAt(EXPIRES_AT);
    }
}

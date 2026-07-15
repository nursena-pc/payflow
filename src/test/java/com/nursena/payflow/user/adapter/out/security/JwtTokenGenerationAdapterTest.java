package com.nursena.payflow.user.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import com.nursena.payflow.user.application.port.out.GeneratedAccessToken;
import com.nursena.payflow.user.domain.model.EmailAddress;
import com.nursena.payflow.user.domain.model.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

class JwtTokenGenerationAdapterTest {

    private static final Instant NOW =
        Instant.parse("2026-07-14T12:00:00Z");

    @Test
    void shouldGenerateAccessTokenWithUserClaims() {
        JwtEncoder jwtEncoder = mock(JwtEncoder.class);
        Jwt encodedJwt = mock(Jwt.class);

        when(encodedJwt.getTokenValue())
            .thenReturn("signed-access-token");

        when(jwtEncoder.encode(any(JwtEncoderParameters.class)))
            .thenReturn(encodedJwt);

        JwtProperties properties = new JwtProperties(
            "https://api.payflow.local",
            Duration.ofMinutes(15)
        );

        Clock clock = Clock.fixed(
            NOW,
            ZoneOffset.UTC
        );

        JwtTokenGenerationAdapter adapter =
            new JwtTokenGenerationAdapter(
                jwtEncoder,
                properties,
                clock
            );

        User user = User.register(
            EmailAddress.of("nursena@example.com"),
            "$2a$12$hashed-password",
            NOW
        );

        GeneratedAccessToken result =
            adapter.generate(user);

        assertThat(result.value())
            .isEqualTo("signed-access-token");

        assertThat(result.expiresAt())
            .isEqualTo(NOW.plusSeconds(900));

        ArgumentCaptor<JwtEncoderParameters> captor =
            ArgumentCaptor.forClass(
                JwtEncoderParameters.class
            );

        verify(jwtEncoder).encode(captor.capture());

        JwtClaimsSet claims =
            captor.getValue().getClaims();

        assertThat(claims.getIssuer())
            .hasToString("https://api.payflow.local");

        assertThat(claims.getSubject())
            .isEqualTo(user.id().toString());

        assertThat(claims.getIssuedAt())
            .isEqualTo(NOW);

        assertThat(claims.getExpiresAt())
            .isEqualTo(NOW.plusSeconds(900));

        assertThat(claims.getClaimAsString("email"))
            .isEqualTo("nursena@example.com");

        assertThat(claims.getClaimAsString("role"))
            .isEqualTo(user.role().name());
    }
}

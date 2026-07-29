package com.nursena.payflow.user.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class RotateRefreshCredentialsResultTest {

    private static final Instant ACCESS_EXPIRES_AT =
        Instant.parse(
            "2026-07-28T12:15:00Z"
        );

    private static final Instant REFRESH_EXPIRES_AT =
        Instant.parse(
            "2026-08-04T12:00:00Z"
        );

    @Test
    void shouldRetainCredentialPair() {
        RotateRefreshCredentialsResult result =
            new RotateRefreshCredentialsResult(
                "signed-access-token",
                ACCESS_EXPIRES_AT,
                "opaque-refresh-token",
                REFRESH_EXPIRES_AT
            );

        assertThat(result.accessToken())
            .isEqualTo(
                "signed-access-token"
            );

        assertThat(result.expiresAt())
            .isEqualTo(
                ACCESS_EXPIRES_AT
            );

        assertThat(result.refreshToken())
            .isEqualTo(
                "opaque-refresh-token"
            );

        assertThat(
            result.refreshTokenExpiresAt()
        )
            .isEqualTo(
                REFRESH_EXPIRES_AT
            );
    }

    @Test
    void shouldRejectBlankAccessToken() {
        assertThatThrownBy(() ->
            new RotateRefreshCredentialsResult(
                " ",
                ACCESS_EXPIRES_AT,
                "refresh-token",
                REFRESH_EXPIRES_AT
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "accessToken must not be blank"
            );
    }

    @Test
    void shouldRejectBlankRefreshToken() {
        assertThatThrownBy(() ->
            new RotateRefreshCredentialsResult(
                "access-token",
                ACCESS_EXPIRES_AT,
                " ",
                REFRESH_EXPIRES_AT
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "refreshToken must not be blank"
            );
    }

    @Test
    void shouldRequireCredentialExpirations() {
        assertThatThrownBy(() ->
            new RotateRefreshCredentialsResult(
                "access-token",
                null,
                "refresh-token",
                REFRESH_EXPIRES_AT
            )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "expiresAt must not be null"
            );

        assertThatThrownBy(() ->
            new RotateRefreshCredentialsResult(
                "access-token",
                ACCESS_EXPIRES_AT,
                "refresh-token",
                null
            )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "refreshTokenExpiresAt must not be null"
            );
    }

    @Test
    void shouldRedactCredentialsFromToString() {
        RotateRefreshCredentialsResult result =
            new RotateRefreshCredentialsResult(
                "secret-access-token",
                ACCESS_EXPIRES_AT,
                "secret-refresh-token",
                REFRESH_EXPIRES_AT
            );

        assertThat(result.toString())
            .isEqualTo(
                "RotateRefreshCredentialsResult[redacted]"
            )
            .doesNotContain(
                "secret-access-token",
                "secret-refresh-token"
            );
    }
}

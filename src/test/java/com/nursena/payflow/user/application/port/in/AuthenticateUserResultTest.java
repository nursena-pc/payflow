package com.nursena.payflow.user.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuthenticateUserResultTest {

    private static final Instant ACCESS_EXPIRY = Instant.parse("2026-08-08T12:15:00Z");
    private static final Instant REFRESH_EXPIRY = Instant.parse("2026-08-15T12:00:00Z");
    private static final Instant CHALLENGE_EXPIRY = Instant.parse("2026-08-08T12:05:00Z");

    @Test
    void shouldRepresentCompletedAuthentication() {
        AuthenticateUserResult result = new AuthenticatedUserResult(
            "access-token", ACCESS_EXPIRY, "refresh-token", REFRESH_EXPIRY
        );
        assertThat(result).isInstanceOf(AuthenticatedUserResult.class);
    }

    @Test
    void shouldRedactCompletedCredentials() {
        AuthenticatedUserResult result = new AuthenticatedUserResult(
            "access-token", ACCESS_EXPIRY, "refresh-token", REFRESH_EXPIRY
        );
        assertThat(result.toString())
            .isEqualTo("AuthenticatedUserResult[redacted]")
            .doesNotContain("access-token", "refresh-token");
    }

    @Test
    void shouldRejectBlankAccessToken() {
        assertThatThrownBy(() -> new AuthenticatedUserResult(
            " ", ACCESS_EXPIRY, "refresh-token", REFRESH_EXPIRY
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectBlankRefreshToken() {
        assertThatThrownBy(() -> new AuthenticatedUserResult(
            "access-token", ACCESS_EXPIRY, " ", REFRESH_EXPIRY
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRepresentMfaChallengeRequirement() {
        AuthenticateUserResult result = new MfaChallengeRequiredResult(
            "opaque-challenge", CHALLENGE_EXPIRY
        );
        assertThat(result).isInstanceOf(MfaChallengeRequiredResult.class);
    }

    @Test
    void shouldRedactMfaChallenge() {
        MfaChallengeRequiredResult result = new MfaChallengeRequiredResult(
            "opaque-challenge", CHALLENGE_EXPIRY
        );
        assertThat(result.toString())
            .isEqualTo("MfaChallengeRequiredResult[redacted]")
            .doesNotContain("opaque-challenge");
    }

    @Test
    void shouldRejectBlankMfaChallenge() {
        assertThatThrownBy(() -> new MfaChallengeRequiredResult(
            " ", CHALLENGE_EXPIRY
        )).isInstanceOf(IllegalArgumentException.class);
    }
}

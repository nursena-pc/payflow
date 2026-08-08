package com.nursena.payflow.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.user.domain.exception.MfaStateConflictException;
import org.junit.jupiter.api.Test;

class MfaAuthenticatorTest {

    private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void shouldBeginAsPendingEnrollment() {
        MfaAuthenticator authenticator = pending();
        assertThat(authenticator.state()).isEqualTo(MfaLifecycleState.PENDING);
        assertThat(authenticator.isEnrollmentActiveAt(NOW.plusSeconds(60))).isTrue();
    }

    @Test
    void shouldActivatePendingEnrollment() {
        MfaAuthenticator activated = pending().activate(NOW.plusSeconds(60));
        assertThat(activated.state()).isEqualTo(MfaLifecycleState.ENABLED);
        assertThat(activated.activatedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(activated.enrollmentExpiresAt()).isNull();
    }

    @Test
    void shouldRejectActivationAtExpirationBoundary() {
        assertThatThrownBy(() -> pending().activate(NOW.plusSeconds(600)))
            .isInstanceOf(MfaStateConflictException.class);
    }

    @Test
    void shouldRejectEnabledReactivation() {
        MfaAuthenticator activated = pending().activate(NOW.plusSeconds(60));
        assertThatThrownBy(() -> activated.activate(NOW.plusSeconds(120)))
            .isInstanceOf(MfaStateConflictException.class);
    }

    @Test
    void shouldRejectDisabledPersistenceRepresentation() {
        assertThatThrownBy(() -> MfaAuthenticator.rehydrate(
            USER_ID,
            MfaLifecycleState.DISABLED,
            secret(),
            null,
            null,
            NOW,
            NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectInvalidPendingTimestamps() {
        assertThatThrownBy(() -> MfaAuthenticator.rehydrate(
            USER_ID,
            MfaLifecycleState.PENDING,
            secret(),
            NOW,
            null,
            NOW,
            NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static MfaAuthenticator pending() {
        return MfaAuthenticator.beginEnrollment(
            USER_ID,
            secret(),
            NOW,
            NOW.plusSeconds(600)
        );
    }

    private static ProtectedMfaSecret secret() {
        return ProtectedMfaSecret.of(new byte[49]);
    }
}

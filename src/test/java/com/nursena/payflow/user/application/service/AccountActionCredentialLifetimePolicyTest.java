package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions
    .assertThat;

import java.time.Duration;
import java.time.Instant;

import com.nursena.payflow.user.domain.model
    .AccountActionCredentialPurpose;
import org.junit.jupiter.api.Test;

class AccountActionCredentialLifetimePolicyTest {

    private static final Instant ISSUED_AT =
        Instant.parse("2026-08-05T12:00:00Z");

    private final AccountActionCredentialLifetimePolicy
        policy =
        new AccountActionCredentialLifetimePolicy(
            Duration.ofHours(24),
            Duration.ofMinutes(30)
        );

    @Test
    void shouldApplyEmailVerificationLifetime() {
        assertThat(
            policy.expiresAt(
                AccountActionCredentialPurpose
                    .EMAIL_VERIFICATION,
                ISSUED_AT
            )
        )
            .isEqualTo(
                ISSUED_AT.plus(Duration.ofHours(24))
            );
    }

    @Test
    void shouldApplyPasswordRecoveryLifetime() {
        assertThat(
            policy.expiresAt(
                AccountActionCredentialPurpose
                    .PASSWORD_RECOVERY,
                ISSUED_AT
            )
        )
            .isEqualTo(
                ISSUED_AT.plus(Duration.ofMinutes(30))
            );
    }
}

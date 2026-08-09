package com.nursena.payflow.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class MfaRecoveryCodeTest {

    private static final Instant CREATED_AT =
        Instant.parse("2026-08-09T12:00:00Z");

    @Test
    void shouldIssueUsableCodeAndConsumeExactlyOnce() {
        MfaRecoveryCode code = issue();

        assertThat(code.isUsableAt(CREATED_AT)).isTrue();

        MfaRecoveryCode consumed = code.consume(
            CREATED_AT.plusSeconds(1)
        );

        assertThat(consumed.consumedAt())
            .isEqualTo(CREATED_AT.plusSeconds(1));
        assertThat(consumed.isUsableAt(CREATED_AT.plusSeconds(2)))
            .isFalse();
        assertThatThrownBy(() -> consumed.consume(
            CREATED_AT.plusSeconds(2)
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRejectConsumptionBeforeIssuance() {
        assertThatThrownBy(() -> issue().consume(
            CREATED_AT.minusSeconds(1)
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRejectInvalidRehydratedTimestamp() {
        assertThatThrownBy(() -> MfaRecoveryCode.rehydrate(
            UUID.randomUUID(),
            UUID.randomUUID(),
            MfaRecoveryCodeDigest.of(new byte[32]),
            CREATED_AT,
            CREATED_AT.minusSeconds(1)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static MfaRecoveryCode issue() {
        return MfaRecoveryCode.issue(
            UUID.randomUUID(),
            UUID.randomUUID(),
            MfaRecoveryCodeDigest.of(new byte[32]),
            CREATED_AT
        );
    }
}

package com.nursena.payflow.maildelivery.application.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.maildelivery.application.model.MailRetryDecision;
import com.nursena.payflow.maildelivery.domain.model.MailOutboxMessage;
import com.nursena.payflow.maildelivery.domain.model.MailOutboxPurpose;
import com.nursena.payflow.maildelivery.domain.model.ProtectedMailContent;
import org.junit.jupiter.api.Test;

class MailRetryPolicyTest {

    private static final Instant CREATED_AT =
        Instant.parse("2026-08-05T18:00:00Z");

    @Test
    void shouldApplyBoundedExponentialBackoff() {
        MailRetryPolicy policy = new MailRetryPolicy(
            5,
            Duration.ofSeconds(30),
            Duration.ofMinutes(2)
        );
        MailOutboxMessage message = pending(
            CREATED_AT.plus(Duration.ofHours(1))
        ).claim("worker", CREATED_AT, Duration.ofSeconds(20));

        MailRetryDecision decision = policy.decide(
            message,
            CREATED_AT.plusSeconds(1)
        );

        assertThat(decision.shouldRetry()).isTrue();
        assertThat(decision.nextAvailableAt())
            .isEqualTo(CREATED_AT.plusSeconds(31));
    }

    @Test
    void shouldFailTerminallyWhenRetryWouldOutliveCredential() {
        MailRetryPolicy policy = new MailRetryPolicy(
            5,
            Duration.ofSeconds(30),
            Duration.ofMinutes(2)
        );
        MailOutboxMessage message = pending(
            CREATED_AT.plusSeconds(20)
        ).claim("worker", CREATED_AT, Duration.ofSeconds(10));

        MailRetryDecision decision = policy.decide(
            message,
            CREATED_AT.plusSeconds(1)
        );

        assertThat(decision.shouldRetry()).isFalse();
        assertThat(decision.nextAvailableAt()).isNull();
    }

    private static MailOutboxMessage pending(Instant expiresAt) {
        return MailOutboxMessage.pending(
            UUID.randomUUID(),
            UUID.randomUUID(),
            MailOutboxPurpose.PASSWORD_RECOVERY,
            "nursena@example.com",
            "Reset your PayFlow password",
            ProtectedMailContent.of(new byte[]{1, 2, 3}),
            "<account-action-" + UUID.randomUUID() + "@payflow.local>",
            CREATED_AT,
            expiresAt
        );
    }
}

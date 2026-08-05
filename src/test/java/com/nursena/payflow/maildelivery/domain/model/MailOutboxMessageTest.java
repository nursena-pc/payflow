package com.nursena.payflow.maildelivery.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class MailOutboxMessageTest {

    private static final Instant CREATED_AT =
        Instant.parse("2026-08-05T18:00:00Z");
    private static final Instant EXPIRES_AT =
        CREATED_AT.plus(Duration.ofHours(1));

    @Test
    void shouldClaimRetryAndClearProtectedContentAfterDelivery() {
        MailOutboxMessage pending = pendingMessage();
        MailOutboxMessage claimed = pending.claim(
            "mail-worker-1",
            CREATED_AT,
            Duration.ofSeconds(30)
        );

        assertThat(claimed.status()).isEqualTo(MailOutboxStatus.PROCESSING);
        assertThat(claimed.attemptCount()).isEqualTo(1);
        assertThat(claimed.lockedBy()).isEqualTo("mail-worker-1");
        assertThat(claimed.protectionContext().recipient())
            .isEqualTo("nursena@example.com");

        MailOutboxMessage retried = claimed.scheduleRetry(
            "mail-worker-1",
            CREATED_AT.plusSeconds(1),
            CREATED_AT.plusSeconds(31),
            "MailDeliveryException"
        );
        assertThat(retried.status()).isEqualTo(MailOutboxStatus.PENDING);
        assertThat(retried.protectedContent()).isNotNull();
        assertThat(retried.lastError()).isEqualTo("MailDeliveryException");

        MailOutboxMessage reclaimed = retried.claim(
            "mail-worker-2",
            CREATED_AT.plusSeconds(31),
            Duration.ofSeconds(30)
        );
        MailOutboxMessage sent = reclaimed.markSent(
            "mail-worker-2",
            CREATED_AT.plusSeconds(32)
        );

        assertThat(sent.status()).isEqualTo(MailOutboxStatus.SENT);
        assertThat(sent.sentAt()).isEqualTo(CREATED_AT.plusSeconds(32));
        assertThat(sent.protectedContent()).isNull();
        assertThat(sent.lockedBy()).isNull();
        assertThat(sent.toString()).contains("sensitiveContent=redacted");
    }

    @Test
    void shouldRejectWrongLeaseOwnerAndExpiredClaim() {
        MailOutboxMessage claimed = pendingMessage().claim(
            "mail-worker-1",
            CREATED_AT,
            Duration.ofSeconds(30)
        );

        assertThatThrownBy(() -> claimed.markSent(
            "mail-worker-2",
            CREATED_AT.plusSeconds(1)
        )).isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> pendingMessage().claim(
            "mail-worker-1",
            EXPIRES_AT,
            Duration.ofSeconds(30)
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRejectLifecycleTransitionWhenWorkerLeaseIsNotActive() {
        MailOutboxMessage claimed = pendingMessage().claim(
            "mail-worker-1",
            CREATED_AT,
            Duration.ofSeconds(30)
        );
        Instant leaseBoundary = CREATED_AT.plusSeconds(30);

        assertThatThrownBy(() -> claimed.markSent(
            "mail-worker-1",
            leaseBoundary
        )).isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> claimed.scheduleRetry(
            "mail-worker-1",
            leaseBoundary,
            leaseBoundary.plusSeconds(1),
            "MailDeliveryException"
        )).isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> claimed.markFailed(
            "mail-worker-1",
            leaseBoundary,
            "MailDeliveryException"
        )).isInstanceOf(IllegalStateException.class);
    }

    private static MailOutboxMessage pendingMessage() {
        return MailOutboxMessage.pending(
            UUID.fromString("fda97dd3-2fb4-4538-8b11-c4c47fcb5303"),
            UUID.fromString("0a1fd9f5-88a8-4b80-8bb3-121bda9479cc"),
            MailOutboxPurpose.EMAIL_VERIFICATION,
            "nursena@example.com",
            "Verify your PayFlow email",
            ProtectedMailContent.of(new byte[]{1, 2, 3, 4}),
            "<account-action-fda97dd3-2fb4-4538-8b11-c4c47fcb5303@payflow.local>",
            CREATED_AT,
            EXPIRES_AT
        );
    }
}

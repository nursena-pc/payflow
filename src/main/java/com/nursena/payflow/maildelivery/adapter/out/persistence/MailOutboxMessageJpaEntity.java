package com.nursena.payflow.maildelivery.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.maildelivery.domain.model.MailOutboxMessage;
import com.nursena.payflow.maildelivery.domain.model.MailOutboxPurpose;
import com.nursena.payflow.maildelivery.domain.model.MailOutboxStatus;
import com.nursena.payflow.maildelivery.domain.model.ProtectedMailContent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "mail_outbox_messages")
class MailOutboxMessageJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private MailOutboxPurpose purpose;

    @Column(nullable = false, updatable = false, length = 320)
    private String recipient;

    @Column(nullable = false, updatable = false, length = 200)
    private String subject;

    @Column(name = "protected_body")
    private byte[] protectedBody;

    @Column(name = "message_id", nullable = false, updatable = false, unique = true, length = 200)
    private String messageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MailOutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "locked_by", length = 200)
    private String lockedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    protected MailOutboxMessageJpaEntity() {
    }

    MailOutboxMessageJpaEntity(MailOutboxMessage message) {
        this.id = message.id();
        this.userId = message.userId();
        this.purpose = message.purpose();
        this.recipient = message.recipient();
        this.subject = message.subject();
        this.protectedBody = message.protectedContent() == null
            ? null
            : message.protectedContent().value();
        this.messageId = message.messageId();
        applyState(message);
        this.expiresAt = message.expiresAt();
        this.createdAt = message.createdAt();
    }

    void applyState(MailOutboxMessage message) {
        if (id != null && !id.equals(message.id())) {
            throw new IllegalArgumentException("cannot apply another message state");
        }
        status = message.status();
        attemptCount = message.attemptCount();
        availableAt = message.availableAt();
        lockedAt = message.lockedAt();
        lockedUntil = message.lockedUntil();
        lockedBy = message.lockedBy();
        sentAt = message.sentAt();
        lastError = message.lastError();
        protectedBody = message.protectedContent() == null
            ? null
            : message.protectedContent().value();
    }

    UUID getId() { return id; }
    UUID getUserId() { return userId; }
    MailOutboxPurpose getPurpose() { return purpose; }
    String getRecipient() { return recipient; }
    String getSubject() { return subject; }
    ProtectedMailContent getProtectedContent() {
        return protectedBody == null
            ? null
            : ProtectedMailContent.of(protectedBody);
    }
    String getMessageId() { return messageId; }
    MailOutboxStatus getStatus() { return status; }
    int getAttemptCount() { return attemptCount; }
    Instant getAvailableAt() { return availableAt; }
    Instant getExpiresAt() { return expiresAt; }
    Instant getLockedAt() { return lockedAt; }
    Instant getLockedUntil() { return lockedUntil; }
    String getLockedBy() { return lockedBy; }
    Instant getCreatedAt() { return createdAt; }
    Instant getSentAt() { return sentAt; }
    String getLastError() { return lastError; }
}

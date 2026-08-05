package com.nursena.payflow.maildelivery.domain.model;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class MailOutboxMessage {

    private static final int MAX_RECIPIENT_LENGTH = 320;
    private static final int MAX_SUBJECT_LENGTH = 200;
    private static final int MAX_MESSAGE_ID_LENGTH = 200;
    private static final int MAX_WORKER_ID_LENGTH = 200;
    private static final int MAX_ERROR_LENGTH = 1000;

    private final UUID id;
    private final UUID userId;
    private final MailOutboxPurpose purpose;
    private final String recipient;
    private final String subject;
    private final ProtectedMailContent protectedContent;
    private final String messageId;
    private final MailOutboxStatus status;
    private final int attemptCount;
    private final Instant availableAt;
    private final Instant expiresAt;
    private final Instant lockedAt;
    private final Instant lockedUntil;
    private final String lockedBy;
    private final Instant createdAt;
    private final Instant sentAt;
    private final String lastError;

    private MailOutboxMessage(
        UUID id,
        UUID userId,
        MailOutboxPurpose purpose,
        String recipient,
        String subject,
        ProtectedMailContent protectedContent,
        String messageId,
        MailOutboxStatus status,
        int attemptCount,
        Instant availableAt,
        Instant expiresAt,
        Instant lockedAt,
        Instant lockedUntil,
        String lockedBy,
        Instant createdAt,
        Instant sentAt,
        String lastError
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.purpose = Objects.requireNonNull(purpose, "purpose must not be null");
        this.recipient = requireBounded(recipient, "recipient", MAX_RECIPIENT_LENGTH);
        this.subject = requireBounded(subject, "subject", MAX_SUBJECT_LENGTH);
        this.protectedContent = protectedContent;
        this.messageId = requireBounded(messageId, "messageId", MAX_MESSAGE_ID_LENGTH);
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.attemptCount = attemptCount;
        this.availableAt = Objects.requireNonNull(availableAt, "availableAt must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.lockedAt = lockedAt;
        this.lockedUntil = lockedUntil;
        this.lockedBy = lockedBy;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.sentAt = sentAt;
        this.lastError = lastError;
        validateState();
    }

    public static MailOutboxMessage pending(
        UUID id,
        UUID userId,
        MailOutboxPurpose purpose,
        String recipient,
        String subject,
        ProtectedMailContent protectedContent,
        String messageId,
        Instant createdAt,
        Instant expiresAt
    ) {
        return new MailOutboxMessage(
            id,
            userId,
            purpose,
            recipient,
            subject,
            Objects.requireNonNull(protectedContent, "protectedContent must not be null"),
            messageId,
            MailOutboxStatus.PENDING,
            0,
            createdAt,
            expiresAt,
            null,
            null,
            null,
            createdAt,
            null,
            null
        );
    }

    public static MailOutboxMessage rehydrate(
        UUID id,
        UUID userId,
        MailOutboxPurpose purpose,
        String recipient,
        String subject,
        ProtectedMailContent protectedContent,
        String messageId,
        MailOutboxStatus status,
        int attemptCount,
        Instant availableAt,
        Instant expiresAt,
        Instant lockedAt,
        Instant lockedUntil,
        String lockedBy,
        Instant createdAt,
        Instant sentAt,
        String lastError
    ) {
        return new MailOutboxMessage(
            id,
            userId,
            purpose,
            recipient,
            subject,
            protectedContent,
            messageId,
            status,
            attemptCount,
            availableAt,
            expiresAt,
            lockedAt,
            lockedUntil,
            lockedBy,
            createdAt,
            sentAt,
            lastError
        );
    }

    public MailOutboxMessage claim(
        String workerId,
        Instant claimedAt,
        Duration leaseDuration
    ) {
        String checkedWorkerId = requireBounded(
            workerId,
            "workerId",
            MAX_WORKER_ID_LENGTH
        );
        Instant checkedClaimedAt = Objects.requireNonNull(
            claimedAt,
            "claimedAt must not be null"
        );
        Duration checkedLease = requirePositive(leaseDuration, "leaseDuration");

        boolean claimablePending =
            status == MailOutboxStatus.PENDING
                && !availableAt.isAfter(checkedClaimedAt);
        boolean claimableExpiredLease =
            status == MailOutboxStatus.PROCESSING
                && !lockedUntil.isAfter(checkedClaimedAt);

        if ((!claimablePending && !claimableExpiredLease)
            || !expiresAt.isAfter(checkedClaimedAt)) {
            throw new IllegalStateException("mail message is not claimable");
        }

        Instant leaseUntil;
        try {
            leaseUntil = checkedClaimedAt.plus(checkedLease);
        } catch (DateTimeException | ArithmeticException exception) {
            throw new IllegalArgumentException("leaseDuration produces an invalid lock", exception);
        }

        return new MailOutboxMessage(
            id,
            userId,
            purpose,
            recipient,
            subject,
            protectedContent,
            messageId,
            MailOutboxStatus.PROCESSING,
            Math.addExact(attemptCount, 1),
            availableAt,
            expiresAt,
            checkedClaimedAt,
            leaseUntil,
            checkedWorkerId,
            createdAt,
            null,
            lastError
        );
    }

    public MailOutboxMessage markSent(
        String workerId,
        Instant deliveredAt
    ) {
        Instant checkedDeliveredAt = Objects.requireNonNull(
            deliveredAt,
            "deliveredAt must not be null"
        );
        requireActiveLeaseOwnedBy(workerId, checkedDeliveredAt);

        if (checkedDeliveredAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("deliveredAt must not be before createdAt");
        }

        return terminal(
            MailOutboxStatus.SENT,
            checkedDeliveredAt,
            null
        );
    }

    public MailOutboxMessage scheduleRetry(
        String workerId,
        Instant failedAt,
        Instant nextAvailableAt,
        String error
    ) {
        Instant checkedFailedAt = Objects.requireNonNull(
            failedAt,
            "failedAt must not be null"
        );
        requireActiveLeaseOwnedBy(workerId, checkedFailedAt);
        Instant checkedNext = Objects.requireNonNull(nextAvailableAt, "nextAvailableAt must not be null");
        String checkedError = requireBounded(error, "error", MAX_ERROR_LENGTH);

        if (checkedNext.isBefore(checkedFailedAt)) {
            throw new IllegalArgumentException("nextAvailableAt must not be before failedAt");
        }

        return new MailOutboxMessage(
            id,
            userId,
            purpose,
            recipient,
            subject,
            protectedContent,
            messageId,
            MailOutboxStatus.PENDING,
            attemptCount,
            checkedNext,
            expiresAt,
            null,
            null,
            null,
            createdAt,
            null,
            checkedError
        );
    }

    public MailOutboxMessage markFailed(
        String workerId,
        Instant failedAt,
        String error
    ) {
        Instant checkedFailedAt = Objects.requireNonNull(
            failedAt,
            "failedAt must not be null"
        );
        requireActiveLeaseOwnedBy(workerId, checkedFailedAt);
        return terminal(
            MailOutboxStatus.FAILED,
            null,
            requireBounded(error, "error", MAX_ERROR_LENGTH)
        );
    }

    public MailOutboxMessage failWithoutClaim(
        Instant failedAt,
        String error
    ) {
        Objects.requireNonNull(failedAt, "failedAt must not be null");
        if (status == MailOutboxStatus.SENT || status == MailOutboxStatus.FAILED) {
            return this;
        }
        return terminal(
            MailOutboxStatus.FAILED,
            null,
            requireBounded(error, "error", MAX_ERROR_LENGTH)
        );
    }

    private MailOutboxMessage terminal(
        MailOutboxStatus terminalStatus,
        Instant terminalSentAt,
        String terminalError
    ) {
        return new MailOutboxMessage(
            id,
            userId,
            purpose,
            recipient,
            subject,
            null,
            messageId,
            terminalStatus,
            attemptCount,
            availableAt,
            expiresAt,
            null,
            null,
            null,
            createdAt,
            terminalSentAt,
            terminalError
        );
    }

    private void requireActiveLeaseOwnedBy(
        String workerId,
        Instant transitionAt
    ) {
        String checkedWorkerId = requireBounded(
            workerId,
            "workerId",
            MAX_WORKER_ID_LENGTH
        );
        Instant checkedTransitionAt = Objects.requireNonNull(
            transitionAt,
            "transitionAt must not be null"
        );
        if (status != MailOutboxStatus.PROCESSING
            || !checkedWorkerId.equals(lockedBy)
            || checkedTransitionAt.isBefore(lockedAt)
            || !checkedTransitionAt.isBefore(lockedUntil)) {
            throw new IllegalStateException(
                "mail message does not have an active lease owned by this worker"
            );
        }
    }

    private void validateState() {
        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount must not be negative");
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
        if (availableAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("availableAt must not be before createdAt");
        }
        boolean processing = status == MailOutboxStatus.PROCESSING;
        boolean hasCompleteLock = lockedAt != null && lockedUntil != null && lockedBy != null;
        if (processing != hasCompleteLock) {
            throw new IllegalArgumentException("processing lock state is inconsistent");
        }
        if (processing && !lockedUntil.isAfter(lockedAt)) {
            throw new IllegalArgumentException("lockedUntil must be after lockedAt");
        }
        boolean terminal = status == MailOutboxStatus.SENT || status == MailOutboxStatus.FAILED;
        if (terminal == (protectedContent != null)) {
            throw new IllegalArgumentException("protectedContent lifecycle is inconsistent");
        }
        if ((status == MailOutboxStatus.SENT) != (sentAt != null)) {
            throw new IllegalArgumentException("sentAt lifecycle is inconsistent");
        }
        if (lastError != null && lastError.length() > MAX_ERROR_LENGTH) {
            throw new IllegalArgumentException("lastError is too long");
        }
    }

    private static String requireBounded(String value, String field, int maxLength) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be non-blank and at most " + maxLength + " characters");
        }
        return value;
    }

    private static Duration requirePositive(Duration value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    public UUID id() { return id; }
    public UUID userId() { return userId; }
    public MailOutboxPurpose purpose() { return purpose; }
    public String recipient() { return recipient; }
    public String subject() { return subject; }
    public ProtectedMailContent protectedContent() { return protectedContent; }
    public MailContentProtectionContext protectionContext() {
        return new MailContentProtectionContext(
            id,
            userId,
            purpose,
            recipient,
            subject
        );
    }
    public String messageId() { return messageId; }
    public MailOutboxStatus status() { return status; }
    public int attemptCount() { return attemptCount; }
    public Instant availableAt() { return availableAt; }
    public Instant expiresAt() { return expiresAt; }
    public Instant lockedAt() { return lockedAt; }
    public Instant lockedUntil() { return lockedUntil; }
    public String lockedBy() { return lockedBy; }
    public Instant createdAt() { return createdAt; }
    public Instant sentAt() { return sentAt; }
    public String lastError() { return lastError; }

    @Override
    public String toString() {
        return "MailOutboxMessage[id=" + id
            + ", purpose=" + purpose
            + ", status=" + status
            + ", attemptCount=" + attemptCount
            + ", sensitiveContent=redacted]";
    }
}

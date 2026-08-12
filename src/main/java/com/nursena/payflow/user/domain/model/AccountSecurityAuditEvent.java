package com.nursena.payflow.user.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AccountSecurityAuditEvent(
    UUID id,
    UUID subjectUserId,
    AccountSecurityAuditAction action,
    Instant occurredAt
) {

    public AccountSecurityAuditEvent {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(subjectUserId, "subjectUserId must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    public static AccountSecurityAuditEvent mfaDisabled(
        UUID id,
        UUID subjectUserId,
        Instant occurredAt
    ) {
        return new AccountSecurityAuditEvent(
            id,
            subjectUserId,
            AccountSecurityAuditAction.MFA_DISABLED,
            occurredAt
        );
    }

    public static AccountSecurityAuditEvent recoveryCodesRotated(
        UUID id,
        UUID subjectUserId,
        Instant occurredAt
    ) {
        return new AccountSecurityAuditEvent(
            id,
            subjectUserId,
            AccountSecurityAuditAction.RECOVERY_CODES_ROTATED,
            occurredAt
        );
    }
}
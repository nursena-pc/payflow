package com.nursena.payflow.user.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.user.domain.model.AccountSecurityAuditAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "account_security_audits")
class AccountSecurityAuditJpaEntity {

    @Id
    @Column(
        name = "id",
        nullable = false,
        updatable = false
    )
    private UUID id;

    @Column(
        name = "subject_user_id",
        nullable = false,
        updatable = false
    )
    private UUID subjectUserId;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "action",
        nullable = false,
        updatable = false,
        length = 64
    )
    private AccountSecurityAuditAction action;

    @Column(
        name = "occurred_at",
        nullable = false,
        updatable = false
    )
    private Instant occurredAt;

    protected AccountSecurityAuditJpaEntity() {
    }

    AccountSecurityAuditJpaEntity(
        UUID id,
        UUID subjectUserId,
        AccountSecurityAuditAction action,
        Instant occurredAt
    ) {
        this.id = id;
        this.subjectUserId = subjectUserId;
        this.action = action;
        this.occurredAt = occurredAt;
    }

    UUID getId() {
        return id;
    }

    UUID getSubjectUserId() {
        return subjectUserId;
    }

    AccountSecurityAuditAction getAction() {
        return action;
    }

    Instant getOccurredAt() {
        return occurredAt;
    }
}

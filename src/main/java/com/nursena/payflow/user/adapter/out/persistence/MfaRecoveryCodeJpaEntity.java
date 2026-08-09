package com.nursena.payflow.user.adapter.out.persistence;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "mfa_recovery_codes")
class MfaRecoveryCodeJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "code_digest", nullable = false, updatable = false)
    private byte[] codeDigest;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected MfaRecoveryCodeJpaEntity() {
    }

    MfaRecoveryCodeJpaEntity(
        UUID id,
        UUID userId,
        byte[] codeDigest,
        Instant createdAt,
        Instant consumedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.codeDigest = Arrays.copyOf(
            codeDigest,
            codeDigest.length
        );
        this.createdAt = createdAt;
        this.consumedAt = consumedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getUserId() {
        return userId;
    }

    byte[] getCodeDigest() {
        return Arrays.copyOf(codeDigest, codeDigest.length);
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getConsumedAt() {
        return consumedAt;
    }
}

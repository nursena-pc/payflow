package com.nursena.payflow.user.adapter.out.persistence;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "step_up_grants")
class StepUpGrantJpaEntity {

    @Id
    private UUID id;

    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;

    @Column(name = "purpose", nullable = false, updatable = false)
    private String purpose;

    @Column(name = "grant_digest", nullable = false, updatable = false)
    private byte[] grantDigest;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    protected StepUpGrantJpaEntity() {
    }

    StepUpGrantJpaEntity(
        UUID id,
        UUID subjectId,
        String purpose,
        byte[] grantDigest,
        Instant issuedAt,
        Instant expiresAt,
        Instant consumedAt,
        Instant supersededAt
    ) {
        this.id = id;
        this.subjectId = subjectId;
        this.purpose = purpose;
        this.grantDigest = Arrays.copyOf(grantDigest, grantDigest.length);
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.consumedAt = consumedAt;
        this.supersededAt = supersededAt;
    }

    UUID getId() { return id; }
    UUID getSubjectId() { return subjectId; }
    String getPurpose() { return purpose; }
    byte[] getGrantDigest() { return Arrays.copyOf(grantDigest, grantDigest.length); }
    Instant getIssuedAt() { return issuedAt; }
    Instant getExpiresAt() { return expiresAt; }
    Instant getConsumedAt() { return consumedAt; }
    Instant getSupersededAt() { return supersededAt; }
}

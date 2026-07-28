package com.nursena.payflow.user.adapter.out.persistence;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "refresh_token_records")
class RefreshTokenRecordJpaEntity {

    @Id
    private UUID id;

    @Column(
        name = "family_id",
        nullable = false,
        updatable = false
    )
    private UUID familyId;

    @Column(
        name = "token_digest",
        nullable = false,
        updatable = false
    )
    private byte[] tokenDigest;

    @Column(
        name = "issued_at",
        nullable = false,
        updatable = false
    )
    private Instant issuedAt;

    @Column(
        name = "expires_at",
        nullable = false,
        updatable = false
    )
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "successor_id")
    private UUID successorId;

    protected RefreshTokenRecordJpaEntity() {
    }

    RefreshTokenRecordJpaEntity(
        UUID id,
        UUID familyId,
        byte[] tokenDigest,
        Instant issuedAt,
        Instant expiresAt,
        Instant consumedAt,
        UUID successorId
    ) {
        this.id = id;
        this.familyId = familyId;
        this.tokenDigest =
            copyDigest(tokenDigest);
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.consumedAt = consumedAt;
        this.successorId = successorId;
    }

    UUID getId() {
        return id;
    }

    UUID getFamilyId() {
        return familyId;
    }

    byte[] getTokenDigest() {
        return copyDigest(tokenDigest);
    }

    Instant getIssuedAt() {
        return issuedAt;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    Instant getConsumedAt() {
        return consumedAt;
    }

    UUID getSuccessorId() {
        return successorId;
    }

    private static byte[] copyDigest(
        byte[] source
    ) {
        if (source == null) {
            return null;
        }

        return Arrays.copyOf(
            source,
            source.length
        );
    }
}

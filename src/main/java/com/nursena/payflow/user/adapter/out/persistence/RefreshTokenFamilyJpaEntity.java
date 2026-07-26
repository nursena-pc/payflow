package com.nursena.payflow.user.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.user.domain.model.RefreshTokenFamilyRevocationReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "refresh_token_families")
class RefreshTokenFamilyJpaEntity {

    @Id
    private UUID id;

    @Column(
        name = "user_id",
        nullable = false,
        updatable = false
    )
    private UUID userId;

    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private Instant createdAt;

    @Column(
        name = "expires_at",
        nullable = false
    )
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "revocation_reason",
        length = 40
    )
    private RefreshTokenFamilyRevocationReason
        revocationReason;

    protected RefreshTokenFamilyJpaEntity() {
    }

    RefreshTokenFamilyJpaEntity(
        UUID id,
        UUID userId,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt,
        RefreshTokenFamilyRevocationReason
            revocationReason
    ) {
        this.id = id;
        this.userId = userId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
        this.revocationReason = revocationReason;
    }

    UUID getId() {
        return id;
    }

    UUID getUserId() {
        return userId;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    Instant getRevokedAt() {
        return revokedAt;
    }

    RefreshTokenFamilyRevocationReason
    getRevocationReason() {
        return revocationReason;
    }
}

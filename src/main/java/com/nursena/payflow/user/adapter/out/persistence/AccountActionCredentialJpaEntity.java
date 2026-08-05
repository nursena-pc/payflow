package com.nursena.payflow.user.adapter.out.persistence;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import com.nursena.payflow.user.domain.model
    .AccountActionCredentialPurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "account_action_credentials")
class AccountActionCredentialJpaEntity {

    @Id
    private UUID id;

    @Column(
        name = "user_id",
        nullable = false,
        updatable = false
    )
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(
        nullable = false,
        updatable = false,
        length = 32
    )
    private AccountActionCredentialPurpose purpose;

    @Column(
        name = "credential_digest",
        nullable = false,
        updatable = false
    )
    private byte[] credentialDigest;

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

    @Column(name = "superseded_at")
    private Instant supersededAt;

    protected AccountActionCredentialJpaEntity() {
    }

    AccountActionCredentialJpaEntity(
        UUID id,
        UUID userId,
        AccountActionCredentialPurpose purpose,
        byte[] credentialDigest,
        Instant issuedAt,
        Instant expiresAt,
        Instant consumedAt,
        Instant supersededAt
    ) {
        this.id = id;
        this.userId = userId;
        this.purpose = purpose;
        this.credentialDigest = copyDigest(
            credentialDigest
        );
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.consumedAt = consumedAt;
        this.supersededAt = supersededAt;
    }

    UUID getId() {
        return id;
    }

    UUID getUserId() {
        return userId;
    }

    AccountActionCredentialPurpose getPurpose() {
        return purpose;
    }

    byte[] getCredentialDigest() {
        return copyDigest(credentialDigest);
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

    Instant getSupersededAt() {
        return supersededAt;
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

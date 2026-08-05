package com.nursena.payflow.user.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.user.domain.exception
    .InvalidAccountActionCredentialException;

public final class AccountActionCredential {

    private final AccountActionCredentialId id;
    private final UUID userId;
    private final AccountActionCredentialPurpose purpose;
    private final AccountActionCredentialDigest digest;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private final Instant consumedAt;
    private final Instant supersededAt;

    private AccountActionCredential(
        AccountActionCredentialId id,
        UUID userId,
        AccountActionCredentialPurpose purpose,
        AccountActionCredentialDigest digest,
        Instant issuedAt,
        Instant expiresAt,
        Instant consumedAt,
        Instant supersededAt
    ) {
        this.id = Objects.requireNonNull(
            id,
            "id must not be null"
        );
        this.userId = Objects.requireNonNull(
            userId,
            "userId must not be null"
        );
        this.purpose = Objects.requireNonNull(
            purpose,
            "purpose must not be null"
        );
        this.digest = Objects.requireNonNull(
            digest,
            "digest must not be null"
        );
        this.issuedAt = Objects.requireNonNull(
            issuedAt,
            "issuedAt must not be null"
        );
        this.expiresAt = Objects.requireNonNull(
            expiresAt,
            "expiresAt must not be null"
        );
        this.consumedAt = consumedAt;
        this.supersededAt = supersededAt;

        validateState();
    }

    public static AccountActionCredential issue(
        AccountActionCredentialId id,
        UUID userId,
        AccountActionCredentialPurpose purpose,
        AccountActionCredentialDigest digest,
        Instant issuedAt,
        Instant expiresAt
    ) {
        return new AccountActionCredential(
            id,
            userId,
            purpose,
            digest,
            issuedAt,
            expiresAt,
            null,
            null
        );
    }

    public static AccountActionCredential rehydrate(
        AccountActionCredentialId id,
        UUID userId,
        AccountActionCredentialPurpose purpose,
        AccountActionCredentialDigest digest,
        Instant issuedAt,
        Instant expiresAt,
        Instant consumedAt,
        Instant supersededAt
    ) {
        return new AccountActionCredential(
            id,
            userId,
            purpose,
            digest,
            issuedAt,
            expiresAt,
            consumedAt,
            supersededAt
        );
    }

    public AccountActionCredential consume(
        Instant now
    ) {
        Instant consumedOn = Objects.requireNonNull(
            now,
            "now must not be null"
        );

        if (!isActiveAt(consumedOn)) {
            throw new
                InvalidAccountActionCredentialException();
        }

        return new AccountActionCredential(
            id,
            userId,
            purpose,
            digest,
            issuedAt,
            expiresAt,
            consumedOn,
            null
        );
    }

    public AccountActionCredential supersede(
        Instant now
    ) {
        Instant supersededOn = Objects.requireNonNull(
            now,
            "now must not be null"
        );

        if (isResolved()) {
            return this;
        }

        if (supersededOn.isBefore(issuedAt)) {
            throw new IllegalArgumentException(
                "supersededAt must not be before issuedAt"
            );
        }

        return new AccountActionCredential(
            id,
            userId,
            purpose,
            digest,
            issuedAt,
            expiresAt,
            null,
            supersededOn
        );
    }

    public boolean isActiveAt(
        Instant now
    ) {
        Instant checkedAt = Objects.requireNonNull(
            now,
            "now must not be null"
        );

        return !checkedAt.isBefore(issuedAt)
            && expiresAt.isAfter(checkedAt)
            && !isResolved();
    }

    public boolean isResolved() {
        return consumedAt != null
            || supersededAt != null;
    }

    private void validateState() {
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException(
                "expiresAt must be after issuedAt"
            );
        }

        if (
            consumedAt != null
                && supersededAt != null
        ) {
            throw new IllegalArgumentException(
                "consumedAt and supersededAt "
                    + "must not appear together"
            );
        }

        if (
            consumedAt != null
                && (
                    consumedAt.isBefore(issuedAt)
                        || !consumedAt.isBefore(expiresAt)
                )
        ) {
            throw new IllegalArgumentException(
                "consumedAt must be within credential lifetime"
            );
        }

        if (
            supersededAt != null
                && supersededAt.isBefore(issuedAt)
        ) {
            throw new IllegalArgumentException(
                "supersededAt must not be before issuedAt"
            );
        }
    }

    public AccountActionCredentialId id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public AccountActionCredentialPurpose purpose() {
        return purpose;
    }

    public AccountActionCredentialDigest digest() {
        return digest;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant consumedAt() {
        return consumedAt;
    }

    public Instant supersededAt() {
        return supersededAt;
    }
}

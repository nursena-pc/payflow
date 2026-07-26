package com.nursena.payflow.user.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class RefreshTokenFamily {

    private final RefreshTokenFamilyId id;
    private final UUID userId;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final Instant revokedAt;
    private final RefreshTokenFamilyRevocationReason
        revocationReason;

    private RefreshTokenFamily(
        RefreshTokenFamilyId id,
        UUID userId,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt,
        RefreshTokenFamilyRevocationReason
            revocationReason
    ) {
        this.id =
            Objects.requireNonNull(
                id,
                "id must not be null"
            );

        this.userId =
            Objects.requireNonNull(
                userId,
                "userId must not be null"
            );

        this.createdAt =
            Objects.requireNonNull(
                createdAt,
                "createdAt must not be null"
            );

        this.expiresAt =
            Objects.requireNonNull(
                expiresAt,
                "expiresAt must not be null"
            );

        this.revokedAt = revokedAt;
        this.revocationReason =
            revocationReason;

        validateLifetime();
        validateRevocationState();
    }

    public static RefreshTokenFamily create(
        RefreshTokenFamilyId id,
        UUID userId,
        Instant createdAt,
        Instant expiresAt
    ) {
        return new RefreshTokenFamily(
            id,
            userId,
            createdAt,
            expiresAt,
            null,
            null
        );
    }

    public static RefreshTokenFamily rehydrate(
        RefreshTokenFamilyId id,
        UUID userId,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt,
        RefreshTokenFamilyRevocationReason
            revocationReason
    ) {
        return new RefreshTokenFamily(
            id,
            userId,
            createdAt,
            expiresAt,
            revokedAt,
            revocationReason
        );
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpiredAt(
        Instant now
    ) {
        Instant checkedAt =
            Objects.requireNonNull(
                now,
                "now must not be null"
            );

        return !expiresAt.isAfter(checkedAt);
    }

    public boolean isActiveAt(
        Instant now
    ) {
        Instant checkedAt =
            Objects.requireNonNull(
                now,
                "now must not be null"
            );

        return !checkedAt.isBefore(createdAt)
            && !isRevoked()
            && !isExpiredAt(checkedAt);
    }

    public RefreshTokenFamily revoke(
        RefreshTokenFamilyRevocationReason reason,
        Instant now
    ) {
        RefreshTokenFamilyRevocationReason
            checkedReason =
            Objects.requireNonNull(
                reason,
                "reason must not be null"
            );

        Instant checkedAt =
            Objects.requireNonNull(
                now,
                "now must not be null"
            );

        if (checkedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                "revokedAt must not be before "
                    + "createdAt"
            );
        }

        if (isRevoked()) {
            return this;
        }

        return new RefreshTokenFamily(
            id,
            userId,
            createdAt,
            expiresAt,
            checkedAt,
            checkedReason
        );
    }

    private void validateLifetime() {
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException(
                "expiresAt must be after createdAt"
            );
        }
    }

    private void validateRevocationState() {
        boolean hasRevokedAt =
            revokedAt != null;

        boolean hasReason =
            revocationReason != null;

        if (hasRevokedAt != hasReason) {
            throw new IllegalArgumentException(
                "revokedAt and revocationReason "
                    + "must appear together"
            );
        }

        if (
            revokedAt != null
                && revokedAt.isBefore(createdAt)
        ) {
            throw new IllegalArgumentException(
                "revokedAt must not be before "
                    + "createdAt"
            );
        }
    }

    public RefreshTokenFamilyId id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }

    public RefreshTokenFamilyRevocationReason
    revocationReason() {
        return revocationReason;
    }
}

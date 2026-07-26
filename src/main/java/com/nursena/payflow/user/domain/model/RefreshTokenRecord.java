package com.nursena.payflow.user.domain.model;

import java.time.Instant;
import java.util.Objects;

public final class RefreshTokenRecord {

    private final RefreshTokenRecordId id;
    private final RefreshTokenFamilyId familyId;
    private final RefreshTokenDigest digest;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private final Instant consumedAt;
    private final RefreshTokenRecordId successorId;

    private RefreshTokenRecord(
        RefreshTokenRecordId id,
        RefreshTokenFamilyId familyId,
        RefreshTokenDigest digest,
        Instant issuedAt,
        Instant expiresAt,
        Instant consumedAt,
        RefreshTokenRecordId successorId
    ) {
        this.id =
            Objects.requireNonNull(
                id,
                "id must not be null"
            );

        this.familyId =
            Objects.requireNonNull(
                familyId,
                "familyId must not be null"
            );

        this.digest =
            Objects.requireNonNull(
                digest,
                "digest must not be null"
            );

        this.issuedAt =
            Objects.requireNonNull(
                issuedAt,
                "issuedAt must not be null"
            );

        this.expiresAt =
            Objects.requireNonNull(
                expiresAt,
                "expiresAt must not be null"
            );

        this.consumedAt = consumedAt;
        this.successorId = successorId;

        validateLocalState();
    }

    public static RefreshTokenRecord issue(
        RefreshTokenRecordId id,
        RefreshTokenFamily family,
        RefreshTokenDigest digest,
        Instant issuedAt,
        Instant expiresAt
    ) {
        RefreshTokenFamily checkedFamily =
            Objects.requireNonNull(
                family,
                "family must not be null"
            );

        Instant checkedIssuedAt =
            Objects.requireNonNull(
                issuedAt,
                "issuedAt must not be null"
            );

        if (
            checkedIssuedAt.isBefore(
                checkedFamily.createdAt()
            )
        ) {
            throw new IllegalArgumentException(
                "issuedAt must not be before "
                    + "family createdAt"
            );
        }

        if (
            checkedIssuedAt.isBefore(
                checkedFamily.createdAt()
            )
        ) {
            throw new IllegalArgumentException(
                "issuedAt must not be before "
                    + "family createdAt"
            );
        }

        if (
            !checkedFamily.isActiveAt(
                checkedIssuedAt
            )
        ) {
            throw new IllegalStateException(
                "family must be active when "
                    + "a token is issued"
            );
        }

        RefreshTokenRecord record =
            new RefreshTokenRecord(
                id,
                checkedFamily.id(),
                digest,
                checkedIssuedAt,
                expiresAt,
                null,
                null
            );

        record.validateFamilyState(
            checkedFamily
        );

        return record;
    }

    public static RefreshTokenRecord rehydrate(
        RefreshTokenRecordId id,
        RefreshTokenFamilyId familyId,
        RefreshTokenDigest digest,
        Instant issuedAt,
        Instant expiresAt,
        Instant consumedAt,
        RefreshTokenRecordId successorId,
        RefreshTokenFamily family
    ) {
        RefreshTokenRecord record =
            new RefreshTokenRecord(
                id,
                familyId,
                digest,
                issuedAt,
                expiresAt,
                consumedAt,
                successorId
            );

        record.validateFamilyState(
            Objects.requireNonNull(
                family,
                "family must not be null"
            )
        );

        return record;
    }

    public RefreshTokenRecord consume(
        RefreshTokenRecordId successorId,
        Instant consumedAt,
        RefreshTokenFamily family
    ) {
        RefreshTokenRecordId checkedSuccessorId =
            Objects.requireNonNull(
                successorId,
                "successorId must not be null"
            );

        Instant checkedConsumedAt =
            Objects.requireNonNull(
                consumedAt,
                "consumedAt must not be null"
            );

        RefreshTokenFamily checkedFamily =
            Objects.requireNonNull(
                family,
                "family must not be null"
            );

        validateFamilyIdentity(
            checkedFamily
        );

        if (id.equals(checkedSuccessorId)) {
            throw new IllegalArgumentException(
                "a token must not replace itself"
            );
        }

        if (isConsumed()) {
            throw new IllegalStateException(
                "refresh token is already consumed"
            );
        }

        if (checkedConsumedAt.isBefore(issuedAt)) {
            throw new IllegalArgumentException(
                "consumedAt must not be before "
                    + "issuedAt"
            );
        }

        if (
            !checkedFamily.isActiveAt(
                checkedConsumedAt
            )
        ) {
            throw new IllegalStateException(
                "family must be active when "
                    + "a token is consumed"
            );
        }

        if (!expiresAt.isAfter(checkedConsumedAt)) {
            throw new IllegalStateException(
                "refresh token must not be expired"
            );
        }

        return new RefreshTokenRecord(
            id,
            familyId,
            digest,
            issuedAt,
            expiresAt,
            checkedConsumedAt,
            checkedSuccessorId
        );
    }

    public boolean isConsumed() {
        return consumedAt != null;
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
        RefreshTokenFamily family,
        Instant now
    ) {
        RefreshTokenFamily checkedFamily =
            Objects.requireNonNull(
                family,
                "family must not be null"
            );

        Instant checkedAt =
            Objects.requireNonNull(
                now,
                "now must not be null"
            );

        validateFamilyIdentity(
            checkedFamily
        );

        return !checkedAt.isBefore(issuedAt)
            && !isConsumed()
            && !isExpiredAt(checkedAt)
            && checkedFamily.isActiveAt(checkedAt);
    }

    private void validateLocalState() {
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException(
                "expiresAt must be after issuedAt"
            );
        }

        boolean hasConsumedAt =
            consumedAt != null;

        boolean hasSuccessor =
            successorId != null;

        if (hasConsumedAt != hasSuccessor) {
            throw new IllegalArgumentException(
                "consumedAt and successorId "
                    + "must appear together"
            );
        }

        if (
            successorId != null
                && id.equals(successorId)
        ) {
            throw new IllegalArgumentException(
                "a token must not replace itself"
            );
        }

        if (
            consumedAt != null
                && consumedAt.isBefore(issuedAt)
        ) {
            throw new IllegalArgumentException(
                "consumedAt must not be before "
                    + "issuedAt"
            );
        }

        if (
            consumedAt != null
                && !expiresAt.isAfter(consumedAt)
        ) {
            throw new IllegalArgumentException(
                "consumedAt must be before "
                    + "expiresAt"
            );
        }
    }

    private void validateFamilyState(
        RefreshTokenFamily family
    ) {
        validateFamilyIdentity(family);

        if (issuedAt.isBefore(family.createdAt())) {
            throw new IllegalArgumentException(
                "issuedAt must not be before "
                    + "family createdAt"
            );
        }

        if (expiresAt.isAfter(family.expiresAt())) {
            throw new IllegalArgumentException(
                "token expiresAt must not be after "
                    + "family expiresAt"
            );
        }

        if (
            family.revokedAt() != null
                && !issuedAt.isBefore(
                family.revokedAt()
            )
        ) {
            throw new IllegalArgumentException(
                "issuedAt must be before "
                    + "family revokedAt"
            );
        }

        if (
            consumedAt != null
                && family.revokedAt() != null
                && consumedAt.isAfter(
                family.revokedAt()
            )
        ) {
            throw new IllegalArgumentException(
                "consumedAt must not be after "
                    + "family revokedAt"
            );
        }
    }

    private void validateFamilyIdentity(
        RefreshTokenFamily family
    ) {
        if (!familyId.equals(family.id())) {
            throw new IllegalArgumentException(
                "token familyId must match family id"
            );
        }
    }

    public RefreshTokenRecordId id() {
        return id;
    }

    public RefreshTokenFamilyId familyId() {
        return familyId;
    }

    public RefreshTokenDigest digest() {
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

    public RefreshTokenRecordId successorId() {
        return successorId;
    }
}

package com.nursena.payflow.user.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class MfaRecoveryCode {

    private final UUID id;
    private final UUID userId;
    private final MfaRecoveryCodeDigest digest;
    private final Instant createdAt;
    private final Instant consumedAt;

    private MfaRecoveryCode(
        UUID id,
        UUID userId,
        MfaRecoveryCodeDigest digest,
        Instant createdAt,
        Instant consumedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.userId = Objects.requireNonNull(
            userId,
            "userId must not be null"
        );
        this.digest = Objects.requireNonNull(
            digest,
            "digest must not be null"
        );
        this.createdAt = Objects.requireNonNull(
            createdAt,
            "createdAt must not be null"
        );
        this.consumedAt = consumedAt;

        if (
            consumedAt != null
                && consumedAt.isBefore(createdAt)
        ) {
            throw new IllegalArgumentException(
                "consumedAt must not be before createdAt"
            );
        }
    }

    public static MfaRecoveryCode issue(
        UUID id,
        UUID userId,
        MfaRecoveryCodeDigest digest,
        Instant createdAt
    ) {
        return new MfaRecoveryCode(
            id,
            userId,
            digest,
            createdAt,
            null
        );
    }

    public static MfaRecoveryCode rehydrate(
        UUID id,
        UUID userId,
        MfaRecoveryCodeDigest digest,
        Instant createdAt,
        Instant consumedAt
    ) {
        return new MfaRecoveryCode(
            id,
            userId,
            digest,
            createdAt,
            consumedAt
        );
    }

    public MfaRecoveryCode consume(Instant now) {
        Instant consumedOn = Objects.requireNonNull(
            now,
            "now must not be null"
        );

        if (!isUsableAt(consumedOn)) {
            throw new IllegalStateException(
                "recovery code is not usable"
            );
        }

        return new MfaRecoveryCode(
            id,
            userId,
            digest,
            createdAt,
            consumedOn
        );
    }

    public boolean isUsableAt(Instant now) {
        Instant checkedAt = Objects.requireNonNull(
            now,
            "now must not be null"
        );

        return consumedAt == null
            && !checkedAt.isBefore(createdAt);
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public MfaRecoveryCodeDigest digest() {
        return digest;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant consumedAt() {
        return consumedAt;
    }
}

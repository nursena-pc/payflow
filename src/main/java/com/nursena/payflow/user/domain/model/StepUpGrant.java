package com.nursena.payflow.user.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class StepUpGrant {

    private final UUID id;
    private final UUID subjectId;
    private final StepUpPurpose purpose;
    private final StepUpGrantDigest digest;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private Instant consumedAt;
    private Instant supersededAt;

    private StepUpGrant(
        UUID id,
        UUID subjectId,
        StepUpPurpose purpose,
        StepUpGrantDigest digest,
        Instant issuedAt,
        Instant expiresAt,
        Instant consumedAt,
        Instant supersededAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.subjectId = Objects.requireNonNull(
            subjectId,
            "subjectId must not be null"
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
        validate();
    }

    public static StepUpGrant issue(
        UUID id,
        UUID subjectId,
        StepUpPurpose purpose,
        StepUpGrantDigest digest,
        Instant issuedAt,
        Instant expiresAt
    ) {
        return new StepUpGrant(
            id,
            subjectId,
            purpose,
            digest,
            issuedAt,
            expiresAt,
            null,
            null
        );
    }

    public static StepUpGrant rehydrate(
        UUID id,
        UUID subjectId,
        StepUpPurpose purpose,
        StepUpGrantDigest digest,
        Instant issuedAt,
        Instant expiresAt,
        Instant consumedAt,
        Instant supersededAt
    ) {
        return new StepUpGrant(
            id,
            subjectId,
            purpose,
            digest,
            issuedAt,
            expiresAt,
            consumedAt,
            supersededAt
        );
    }

    public boolean isUsableAt(Instant now) {
        Instant checkedNow = Objects.requireNonNull(
            now,
            "now must not be null"
        );
        return consumedAt == null
            && supersededAt == null
            && expiresAt.isAfter(checkedNow);
    }

    public StepUpGrant consume(Instant now) {
        Instant consumed = Objects.requireNonNull(
            now,
            "now must not be null"
        );
        if (!isUsableAt(consumed)) {
            throw new IllegalStateException("step-up grant is not usable");
        }
        if (consumed.isBefore(issuedAt)) {
            throw new IllegalArgumentException(
                "consumedAt must not be before issuedAt"
            );
        }
        consumedAt = consumed;
        return this;
    }

    private void validate() {
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException(
                "expiresAt must be after issuedAt"
            );
        }
        if (consumedAt != null) {
            if (consumedAt.isBefore(issuedAt)
                || consumedAt.isAfter(expiresAt)) {
                throw new IllegalArgumentException(
                    "consumedAt must be within grant lifetime"
                );
            }
        }
        if (supersededAt != null && supersededAt.isBefore(issuedAt)) {
            throw new IllegalArgumentException(
                "supersededAt must not be before issuedAt"
            );
        }
        if (consumedAt != null && supersededAt != null) {
            throw new IllegalArgumentException(
                "grant cannot be both consumed and superseded"
            );
        }
    }

    public UUID id() { return id; }
    public UUID subjectId() { return subjectId; }
    public StepUpPurpose purpose() { return purpose; }
    public StepUpGrantDigest digest() { return digest; }
    public Instant issuedAt() { return issuedAt; }
    public Instant expiresAt() { return expiresAt; }
    public Instant consumedAt() { return consumedAt; }
    public Instant supersededAt() { return supersededAt; }
}

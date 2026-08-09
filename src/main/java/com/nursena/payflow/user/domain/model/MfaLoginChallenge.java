package com.nursena.payflow.user.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class MfaLoginChallenge {

    private final UUID id;
    private final UUID userId;
    private final MfaLoginChallengeDigest digest;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private final int attemptsRemaining;
    private final MfaLoginChallengeState state;
    private final Instant resolvedAt;

    private MfaLoginChallenge(
        UUID id,
        UUID userId,
        MfaLoginChallengeDigest digest,
        Instant issuedAt,
        Instant expiresAt,
        int attemptsRemaining,
        MfaLoginChallengeState state,
        Instant resolvedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.digest = Objects.requireNonNull(digest, "digest must not be null");
        this.issuedAt = Objects.requireNonNull(
            issuedAt,
            "issuedAt must not be null"
        );
        this.expiresAt = Objects.requireNonNull(
            expiresAt,
            "expiresAt must not be null"
        );
        this.state = Objects.requireNonNull(state, "state must not be null");
        this.attemptsRemaining = attemptsRemaining;
        this.resolvedAt = resolvedAt;
        validate();
    }

    public static MfaLoginChallenge issue(
        UUID id,
        UUID userId,
        MfaLoginChallengeDigest digest,
        Instant issuedAt,
        Instant expiresAt,
        int maxAttempts
    ) {
        if (maxAttempts < 1 || maxAttempts > 10) {
            throw new IllegalArgumentException(
                "maxAttempts must be between 1 and 10"
            );
        }
        return new MfaLoginChallenge(
            id,
            userId,
            digest,
            issuedAt,
            expiresAt,
            maxAttempts,
            MfaLoginChallengeState.PENDING,
            null
        );
    }

    public static MfaLoginChallenge rehydrate(
        UUID id,
        UUID userId,
        MfaLoginChallengeDigest digest,
        Instant issuedAt,
        Instant expiresAt,
        int attemptsRemaining,
        MfaLoginChallengeState state,
        Instant resolvedAt
    ) {
        return new MfaLoginChallenge(
            id,
            userId,
            digest,
            issuedAt,
            expiresAt,
            attemptsRemaining,
            state,
            resolvedAt
        );
    }

    public boolean isPendingAt(Instant now) {
        Instant checkedAt = Objects.requireNonNull(now, "now must not be null");
        return state == MfaLoginChallengeState.PENDING
            && attemptsRemaining > 0
            && !checkedAt.isBefore(issuedAt)
            && expiresAt.isAfter(checkedAt);
    }

    public MfaLoginChallenge failAttempt(Instant now) {
        Instant checkedAt = requirePendingAt(now);
        int remaining = attemptsRemaining - 1;
        if (remaining == 0) {
            return terminal(
                MfaLoginChallengeState.EXHAUSTED,
                checkedAt,
                0
            );
        }
        return copy(
            remaining,
            MfaLoginChallengeState.PENDING,
            null
        );
    }

    public MfaLoginChallenge consume(Instant now) {
        Instant checkedAt = requirePendingAt(now);
        return terminal(
            MfaLoginChallengeState.CONSUMED,
            checkedAt,
            attemptsRemaining
        );
    }

    public MfaLoginChallenge expire(Instant now) {
        Instant checkedAt = Objects.requireNonNull(now, "now must not be null");
        if (state != MfaLoginChallengeState.PENDING) {
            return this;
        }
        if (expiresAt.isAfter(checkedAt)) {
            throw new IllegalStateException(
                "a non-expired challenge cannot be expired"
            );
        }
        return terminal(
            MfaLoginChallengeState.EXPIRED,
            checkedAt,
            attemptsRemaining
        );
    }

    public MfaLoginChallenge supersede(Instant now) {
        Instant checkedAt = Objects.requireNonNull(now, "now must not be null");
        if (state != MfaLoginChallengeState.PENDING) {
            return this;
        }
        return terminal(
            MfaLoginChallengeState.SUPERSEDED,
            checkedAt,
            attemptsRemaining
        );
    }

    private Instant requirePendingAt(Instant now) {
        Instant checkedAt = Objects.requireNonNull(now, "now must not be null");
        if (!isPendingAt(checkedAt)) {
            throw new IllegalStateException(
                "MFA login challenge is not pending"
            );
        }
        return checkedAt;
    }

    private MfaLoginChallenge terminal(
        MfaLoginChallengeState terminalState,
        Instant resolvedOn,
        int remaining
    ) {
        return copy(remaining, terminalState, resolvedOn);
    }

    private MfaLoginChallenge copy(
        int remaining,
        MfaLoginChallengeState nextState,
        Instant resolvedOn
    ) {
        return new MfaLoginChallenge(
            id,
            userId,
            digest,
            issuedAt,
            expiresAt,
            remaining,
            nextState,
            resolvedOn
        );
    }

    private void validate() {
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException(
                "expiresAt must be after issuedAt"
            );
        }
        if (attemptsRemaining < 0 || attemptsRemaining > 10) {
            throw new IllegalArgumentException(
                "attemptsRemaining must be between 0 and 10"
            );
        }
        if (state == MfaLoginChallengeState.PENDING) {
            if (attemptsRemaining == 0 || resolvedAt != null) {
                throw new IllegalArgumentException(
                    "pending challenge state is invalid"
                );
            }
            return;
        }
        if (resolvedAt == null || resolvedAt.isBefore(issuedAt)) {
            throw new IllegalArgumentException(
                "terminal challenge state is invalid"
            );
        }
        if (
            state == MfaLoginChallengeState.EXHAUSTED
                && attemptsRemaining != 0
        ) {
            throw new IllegalArgumentException(
                "exhausted challenge must have zero attempts remaining"
            );
        }
    }

    public UUID id() { return id; }
    public UUID userId() { return userId; }
    public MfaLoginChallengeDigest digest() { return digest; }
    public Instant issuedAt() { return issuedAt; }
    public Instant expiresAt() { return expiresAt; }
    public int attemptsRemaining() { return attemptsRemaining; }
    public MfaLoginChallengeState state() { return state; }
    public Instant resolvedAt() { return resolvedAt; }

    @Override
    public String toString() {
        return "MfaLoginChallenge[id=" + id
            + ", userId=" + userId
            + ", state=" + state
            + ", attemptsRemaining=" + attemptsRemaining
            + "]";
    }
}

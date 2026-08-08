package com.nursena.payflow.user.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.user.domain.exception.MfaStateConflictException;

public final class MfaAuthenticator {

    private final UUID userId;
    private final MfaLifecycleState state;
    private final ProtectedMfaSecret protectedSecret;
    private final Instant enrollmentExpiresAt;
    private final Instant activatedAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    private MfaAuthenticator(
        UUID userId,
        MfaLifecycleState state,
        ProtectedMfaSecret protectedSecret,
        Instant enrollmentExpiresAt,
        Instant activatedAt,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.userId = Objects.requireNonNull(
            userId,
            "userId must not be null"
        );
        this.state = Objects.requireNonNull(
            state,
            "state must not be null"
        );
        this.protectedSecret = Objects.requireNonNull(
            protectedSecret,
            "protectedSecret must not be null"
        );
        this.enrollmentExpiresAt = enrollmentExpiresAt;
        this.activatedAt = activatedAt;
        this.createdAt = Objects.requireNonNull(
            createdAt,
            "createdAt must not be null"
        );
        this.updatedAt = Objects.requireNonNull(
            updatedAt,
            "updatedAt must not be null"
        );

        validateState();
    }

    public static MfaAuthenticator beginEnrollment(
        UUID userId,
        ProtectedMfaSecret protectedSecret,
        Instant now,
        Instant expiresAt
    ) {
        Instant issuedAt = Objects.requireNonNull(
            now,
            "now must not be null"
        );
        Instant checkedExpiresAt = Objects.requireNonNull(
            expiresAt,
            "expiresAt must not be null"
        );

        if (!checkedExpiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException(
                "expiresAt must be after now"
            );
        }

        MfaLifecycle lifecycle =
            MfaLifecycle.disabled().beginEnrollment();

        return new MfaAuthenticator(
            userId,
            lifecycle.state(),
            protectedSecret,
            checkedExpiresAt,
            null,
            issuedAt,
            issuedAt
        );
    }

    public static MfaAuthenticator rehydrate(
        UUID userId,
        MfaLifecycleState state,
        ProtectedMfaSecret protectedSecret,
        Instant enrollmentExpiresAt,
        Instant activatedAt,
        Instant createdAt,
        Instant updatedAt
    ) {
        return new MfaAuthenticator(
            userId,
            state,
            protectedSecret,
            enrollmentExpiresAt,
            activatedAt,
            createdAt,
            updatedAt
        );
    }

    public MfaAuthenticator activate(Instant now) {
        Instant activatedOn = Objects.requireNonNull(
            now,
            "now must not be null"
        );

        if (state != MfaLifecycleState.PENDING) {
            throw new MfaStateConflictException();
        }

        if (!isEnrollmentActiveAt(activatedOn)) {
            throw new MfaStateConflictException();
        }

        MfaLifecycle enabled =
            MfaLifecycle.rehydrate(state).activate();

        return new MfaAuthenticator(
            userId,
            enabled.state(),
            protectedSecret,
            null,
            activatedOn,
            createdAt,
            activatedOn
        );
    }

    public boolean isEnrollmentActiveAt(Instant now) {
        Instant checkedAt = Objects.requireNonNull(
            now,
            "now must not be null"
        );

        return state == MfaLifecycleState.PENDING
            && !checkedAt.isBefore(createdAt)
            && enrollmentExpiresAt.isAfter(checkedAt);
    }

    private void validateState() {
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                "updatedAt must not be before createdAt"
            );
        }

        if (state == MfaLifecycleState.DISABLED) {
            throw new IllegalArgumentException(
                "disabled MFA is represented by the absence of an authenticator"
            );
        }

        if (state == MfaLifecycleState.PENDING) {
            if (
                enrollmentExpiresAt == null
                    || !enrollmentExpiresAt.isAfter(createdAt)
                    || activatedAt != null
            ) {
                throw new IllegalArgumentException(
                    "pending MFA authenticator state is invalid"
                );
            }

            return;
        }

        if (
            enrollmentExpiresAt != null
                || activatedAt == null
                || activatedAt.isBefore(createdAt)
                || updatedAt.isBefore(activatedAt)
        ) {
            throw new IllegalArgumentException(
                "enabled MFA authenticator state is invalid"
            );
        }
    }

    public UUID userId() {
        return userId;
    }

    public MfaLifecycleState state() {
        return state;
    }

    public ProtectedMfaSecret protectedSecret() {
        return protectedSecret;
    }

    public Instant enrollmentExpiresAt() {
        return enrollmentExpiresAt;
    }

    public Instant activatedAt() {
        return activatedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}

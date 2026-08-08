package com.nursena.payflow.user.adapter.out.persistence;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import com.nursena.payflow.user.domain.model.MfaLifecycleState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "mfa_authenticators")
class MfaAuthenticatorJpaEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MfaLifecycleState state;

    @Column(name = "protected_secret", nullable = false)
    private byte[] protectedSecret;

    @Column(name = "enrollment_expires_at")
    private Instant enrollmentExpiresAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MfaAuthenticatorJpaEntity() {
    }

    MfaAuthenticatorJpaEntity(
        UUID userId,
        MfaLifecycleState state,
        byte[] protectedSecret,
        Instant enrollmentExpiresAt,
        Instant activatedAt,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.userId = userId;
        this.state = state;
        this.protectedSecret = copy(protectedSecret);
        this.enrollmentExpiresAt = enrollmentExpiresAt;
        this.activatedAt = activatedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    UUID getUserId() {
        return userId;
    }

    MfaLifecycleState getState() {
        return state;
    }

    byte[] getProtectedSecret() {
        return copy(protectedSecret);
    }

    Instant getEnrollmentExpiresAt() {
        return enrollmentExpiresAt;
    }

    Instant getActivatedAt() {
        return activatedAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    private static byte[] copy(byte[] value) {
        return value == null
            ? null
            : Arrays.copyOf(value, value.length);
    }
}

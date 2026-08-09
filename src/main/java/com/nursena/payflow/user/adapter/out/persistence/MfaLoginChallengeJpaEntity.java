package com.nursena.payflow.user.adapter.out.persistence;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import com.nursena.payflow.user.domain.model.MfaLoginChallengeState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "mfa_login_challenges")
class MfaLoginChallengeJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "challenge_digest", nullable = false, updatable = false)
    private byte[] challengeDigest;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "attempts_remaining", nullable = false)
    private int attemptsRemaining;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MfaLoginChallengeState state;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected MfaLoginChallengeJpaEntity() {
    }

    MfaLoginChallengeJpaEntity(
        UUID id,
        UUID userId,
        byte[] challengeDigest,
        Instant issuedAt,
        Instant expiresAt,
        int attemptsRemaining,
        MfaLoginChallengeState state,
        Instant resolvedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.challengeDigest = Arrays.copyOf(
            challengeDigest,
            challengeDigest.length
        );
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.attemptsRemaining = attemptsRemaining;
        this.state = state;
        this.resolvedAt = resolvedAt;
    }

    UUID getId() { return id; }
    UUID getUserId() { return userId; }
    byte[] getChallengeDigest() {
        return Arrays.copyOf(challengeDigest, challengeDigest.length);
    }
    Instant getIssuedAt() { return issuedAt; }
    Instant getExpiresAt() { return expiresAt; }
    int getAttemptsRemaining() { return attemptsRemaining; }
    MfaLoginChallengeState getState() { return state; }
    Instant getResolvedAt() { return resolvedAt; }
}

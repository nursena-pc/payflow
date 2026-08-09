package com.nursena.payflow.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MfaLoginChallengeTest {

    private static final Instant ISSUED = Instant.parse("2026-08-08T12:00:00Z");
    private static final Instant EXPIRES = ISSUED.plusSeconds(300);
    private static final UUID ID = UUID.fromString("f0031b57-50bc-4452-89f4-0fc1c2071339");
    private static final UUID USER_ID = UUID.fromString("44329e80-8ded-477f-9f14-c72b508133e8");

    @Test
    void shouldIssuePendingChallenge() {
        MfaLoginChallenge challenge = challenge(5);
        assertThat(challenge.state()).isEqualTo(MfaLoginChallengeState.PENDING);
        assertThat(challenge.attemptsRemaining()).isEqualTo(5);
        assertThat(challenge.resolvedAt()).isNull();
    }

    @Test
    void shouldBePendingInsideLifetime() {
        assertThat(challenge(5).isPendingAt(ISSUED.plusSeconds(30))).isTrue();
    }

    @Test
    void shouldNotBePendingAtExpirationBoundary() {
        assertThat(challenge(5).isPendingAt(EXPIRES)).isFalse();
    }

    @Test
    void shouldDecrementInvalidAttemptWithoutResolvingEarly() {
        MfaLoginChallenge failed = challenge(5).failAttempt(ISSUED.plusSeconds(10));
        assertThat(failed.attemptsRemaining()).isEqualTo(4);
        assertThat(failed.state()).isEqualTo(MfaLoginChallengeState.PENDING);
        assertThat(failed.resolvedAt()).isNull();
    }

    @Test
    void shouldExhaustFinalInvalidAttempt() {
        MfaLoginChallenge failed = challenge(1).failAttempt(ISSUED.plusSeconds(10));
        assertThat(failed.attemptsRemaining()).isZero();
        assertThat(failed.state()).isEqualTo(MfaLoginChallengeState.EXHAUSTED);
        assertThat(failed.resolvedAt()).isEqualTo(ISSUED.plusSeconds(10));
    }

    @Test
    void shouldConsumeSuccessfulChallengeExactlyOnce() {
        MfaLoginChallenge consumed = challenge(5).consume(ISSUED.plusSeconds(10));
        assertThat(consumed.state()).isEqualTo(MfaLoginChallengeState.CONSUMED);
        assertThatThrownBy(() -> consumed.consume(ISSUED.plusSeconds(11)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldExpirePendingChallenge() {
        MfaLoginChallenge expired = challenge(5).expire(EXPIRES);
        assertThat(expired.state()).isEqualTo(MfaLoginChallengeState.EXPIRED);
        assertThat(expired.resolvedAt()).isEqualTo(EXPIRES);
    }

    @Test
    void shouldRejectPrematureExpiration() {
        assertThatThrownBy(() -> challenge(5).expire(ISSUED.plusSeconds(1)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldSupersedePendingChallenge() {
        MfaLoginChallenge superseded = challenge(5).supersede(ISSUED.plusSeconds(5));
        assertThat(superseded.state()).isEqualTo(MfaLoginChallengeState.SUPERSEDED);
    }

    @Test
    void shouldRejectInvalidAttemptBound() {
        assertThatThrownBy(() -> challenge(11))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNonIncreasingLifetime() {
        assertThatThrownBy(() -> MfaLoginChallenge.issue(
            ID, USER_ID, digest(), ISSUED, ISSUED, 5
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldNotExposeDigestInToString() {
        assertThat(challenge(5).toString()).doesNotContain("MfaLoginChallengeDigest");
    }

    private static MfaLoginChallenge challenge(int attempts) {
        return MfaLoginChallenge.issue(ID, USER_ID, digest(), ISSUED, EXPIRES, attempts);
    }

    private static MfaLoginChallengeDigest digest() {
        return MfaLoginChallengeDigest.of(new byte[32]);
    }
}

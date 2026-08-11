package com.nursena.payflow.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StepUpGrantTest {

    private static final Instant ISSUED = Instant.parse("2026-08-10T10:00:00Z");
    private static final Instant EXPIRES = ISSUED.plusSeconds(300);

    @Test
    void shouldIssueUsablePurposeBoundGrant() {
        StepUpGrant grant = grant();
        assertThat(grant.subjectId()).isEqualTo(subject());
        assertThat(grant.purpose()).isEqualTo(StepUpPurpose.MFA_DISABLE);
        assertThat(grant.isUsableAt(ISSUED)).isTrue();
        assertThat(grant.isUsableAt(EXPIRES)).isFalse();
    }

    @Test
    void shouldConsumeExactlyOnceWithinLifetime() {
        StepUpGrant grant = grant();
        grant.consume(ISSUED.plusSeconds(10));
        assertThat(grant.consumedAt()).isEqualTo(ISSUED.plusSeconds(10));
        assertThat(grant.isUsableAt(ISSUED.plusSeconds(11))).isFalse();
        assertThatThrownBy(() -> grant.consume(ISSUED.plusSeconds(12)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRejectInvalidLifetime() {
        assertThatThrownBy(() -> StepUpGrant.issue(
            UUID.randomUUID(), subject(), StepUpPurpose.MFA_DISABLE,
            StepUpGrantDigest.of(new byte[32]), ISSUED, ISSUED
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectImpossibleTerminalMetadata() {
        assertThatThrownBy(() -> StepUpGrant.rehydrate(
            UUID.randomUUID(), subject(), StepUpPurpose.MFA_DISABLE,
            StepUpGrantDigest.of(new byte[32]), ISSUED, EXPIRES,
            ISSUED.plusSeconds(10), ISSUED.plusSeconds(20)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldTreatSupersededGrantAsUnusable() {
        StepUpGrant grant = StepUpGrant.rehydrate(
            UUID.randomUUID(), subject(), StepUpPurpose.MFA_DISABLE,
            StepUpGrantDigest.of(new byte[32]), ISSUED, EXPIRES,
            null, ISSUED.plusSeconds(20)
        );
        assertThat(grant.isUsableAt(ISSUED.plusSeconds(30))).isFalse();
    }

    private static StepUpGrant grant() {
        return StepUpGrant.issue(
            UUID.randomUUID(), subject(), StepUpPurpose.MFA_DISABLE,
            StepUpGrantDigest.of(new byte[32]), ISSUED, EXPIRES
        );
    }

    private static UUID subject() {
        return UUID.fromString("10000000-0000-0000-0000-000000000101");
    }
}

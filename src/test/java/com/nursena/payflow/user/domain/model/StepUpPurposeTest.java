package com.nursena.payflow.user.domain.model;

import static org.assertj.core.api.Assertions
    .assertThat;
import static org.assertj.core.api.Assertions
    .assertThatThrownBy;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

class StepUpPurposeTest {

    @Test
    void shouldExposeStableUserPurposes() {
        assertThat(
            EnumSet.of(
                StepUpPurpose.MFA_DISABLE,
                StepUpPurpose.RECOVERY_CODE_ROTATION,
                StepUpPurpose.MFA_AUTHENTICATOR_REPLACEMENT
            )
        )
            .allSatisfy(purpose -> {
                assertThat(purpose.actorKind())
                    .isEqualTo(
                        StepUpActorKind.AUTHENTICATED_USER
                    );
                assertThat(purpose.isOperatorPurpose())
                    .isFalse();
            });
    }

    @Test
    void shouldExposeExplicitOperatorCandidates() {
        assertThat(
            EnumSet.of(
                StepUpPurpose.KAFKA_DEAD_LETTER_REPLAY,
                StepUpPurpose.KAFKA_DEAD_LETTER_DISCARD
            )
        )
            .allSatisfy(purpose -> {
                assertThat(purpose.actorKind())
                    .isEqualTo(
                        StepUpActorKind.PAYFLOW_OPERATOR
                    );
                assertThat(purpose.isOperatorPurpose())
                    .isTrue();
            });
    }

    @Test
    void shouldResolveStablePurposeValue() {
        assertThat(
            StepUpPurpose.fromValue(
                "recovery-code-rotation"
            )
        )
            .isEqualTo(
                StepUpPurpose.RECOVERY_CODE_ROTATION
            );
    }

    @Test
    void shouldRejectUnknownPurposeValueGenerically() {
        assertThatThrownBy(() ->
            StepUpPurpose.fromValue("unknown")
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "step-up purpose is invalid"
            );
    }

    @Test
    void shouldRejectNullPurposeValueGenerically() {
        assertThatThrownBy(() ->
            StepUpPurpose.fromValue(null)
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "step-up purpose is invalid"
            );
    }
}

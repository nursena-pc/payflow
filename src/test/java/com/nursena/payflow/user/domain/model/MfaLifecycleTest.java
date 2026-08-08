package com.nursena.payflow.user.domain.model;

import static org.assertj.core.api.Assertions
    .assertThat;
import static org.assertj.core.api.Assertions
    .assertThatThrownBy;

import com.nursena.payflow.user.domain.exception
    .InvalidMfaLifecycleTransitionException;
import org.junit.jupiter.api.Test;

class MfaLifecycleTest {

    @Test
    void shouldStartDisabled() {
        MfaLifecycle lifecycle =
            MfaLifecycle.disabled();

        assertThat(lifecycle.state())
            .isEqualTo(MfaLifecycleState.DISABLED);
        assertThat(lifecycle.requiresSecondFactor())
            .isFalse();
    }

    @Test
    void shouldBeginEnrollmentFromDisabled() {
        MfaLifecycle pending =
            MfaLifecycle.disabled()
                .beginEnrollment();

        assertThat(pending.state())
            .isEqualTo(MfaLifecycleState.PENDING);
        assertThat(pending.requiresSecondFactor())
            .isFalse();
    }

    @Test
    void shouldActivatePendingEnrollment() {
        MfaLifecycle enabled =
            MfaLifecycle.disabled()
                .beginEnrollment()
                .activate();

        assertThat(enabled.state())
            .isEqualTo(MfaLifecycleState.ENABLED);
        assertThat(enabled.requiresSecondFactor())
            .isTrue();
    }

    @Test
    void shouldCancelPendingEnrollment() {
        MfaLifecycle disabled =
            MfaLifecycle.disabled()
                .beginEnrollment()
                .cancelEnrollment();

        assertThat(disabled.state())
            .isEqualTo(MfaLifecycleState.DISABLED);
    }

    @Test
    void shouldDisableEnabledLifecycle() {
        MfaLifecycle disabled =
            enabledLifecycle().disable();

        assertThat(disabled.state())
            .isEqualTo(MfaLifecycleState.DISABLED);
        assertThat(disabled.requiresSecondFactor())
            .isFalse();
    }

    @Test
    void shouldRejectRepeatedEnrollmentStart() {
        assertInvalidTransition(() ->
            MfaLifecycle.disabled()
                .beginEnrollment()
                .beginEnrollment()
        );
    }

    @Test
    void shouldRejectEnrollmentStartWhenEnabled() {
        assertInvalidTransition(() ->
            enabledLifecycle().beginEnrollment()
        );
    }

    @Test
    void shouldRejectActivationWhenDisabled() {
        assertInvalidTransition(() ->
            MfaLifecycle.disabled().activate()
        );
    }

    @Test
    void shouldRejectRepeatedActivation() {
        assertInvalidTransition(() ->
            enabledLifecycle().activate()
        );
    }

    @Test
    void shouldRejectCancellationWhenDisabled() {
        assertInvalidTransition(() ->
            MfaLifecycle.disabled()
                .cancelEnrollment()
        );
    }

    @Test
    void shouldRejectCancellationWhenEnabled() {
        assertInvalidTransition(() ->
            enabledLifecycle().cancelEnrollment()
        );
    }

    @Test
    void shouldRejectDisableWhenPending() {
        assertInvalidTransition(() ->
            MfaLifecycle.disabled()
                .beginEnrollment()
                .disable()
        );
    }

    @Test
    void shouldRejectDisableWhenAlreadyDisabled() {
        assertInvalidTransition(() ->
            MfaLifecycle.disabled().disable()
        );
    }

    private static MfaLifecycle enabledLifecycle() {
        return MfaLifecycle.disabled()
            .beginEnrollment()
            .activate();
    }

    private static void assertInvalidTransition(
        Runnable action
    ) {
        assertThatThrownBy(action::run)
            .isInstanceOf(
                InvalidMfaLifecycleTransitionException.class
            )
            .hasMessage(
                "MFA state transition is invalid."
            );
    }
}

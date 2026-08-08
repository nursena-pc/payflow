package com.nursena.payflow.user.domain.model;

import java.util.Objects;

import com.nursena.payflow.user.domain.exception
    .InvalidMfaLifecycleTransitionException;

public final class MfaLifecycle {

    private final MfaLifecycleState state;

    private MfaLifecycle(
        MfaLifecycleState state
    ) {
        this.state = Objects.requireNonNull(
            state,
            "state must not be null"
        );
    }

    public static MfaLifecycle disabled() {
        return new MfaLifecycle(
            MfaLifecycleState.DISABLED
        );
    }

    public static MfaLifecycle rehydrate(
        MfaLifecycleState state
    ) {
        return new MfaLifecycle(state);
    }

    public MfaLifecycle beginEnrollment() {
        requireState(MfaLifecycleState.DISABLED);

        return new MfaLifecycle(
            MfaLifecycleState.PENDING
        );
    }

    public MfaLifecycle activate() {
        requireState(MfaLifecycleState.PENDING);

        return new MfaLifecycle(
            MfaLifecycleState.ENABLED
        );
    }

    public MfaLifecycle cancelEnrollment() {
        requireState(MfaLifecycleState.PENDING);

        return disabled();
    }

    public MfaLifecycle disable() {
        requireState(MfaLifecycleState.ENABLED);

        return disabled();
    }

    public boolean requiresSecondFactor() {
        return state.requiresSecondFactor();
    }

    private void requireState(
        MfaLifecycleState expected
    ) {
        if (state != expected) {
            throw new
                InvalidMfaLifecycleTransitionException();
        }
    }

    public MfaLifecycleState state() {
        return state;
    }
}

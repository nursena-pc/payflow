package com.nursena.payflow.user.domain.model;

public enum MfaLifecycleState {
    DISABLED,
    PENDING,
    ENABLED;

    public boolean requiresSecondFactor() {
        return this == ENABLED;
    }
}

package com.nursena.payflow.user.domain.model;

public enum MfaLoginChallengeState {
    PENDING,
    CONSUMED,
    EXHAUSTED,
    EXPIRED,
    SUPERSEDED
}

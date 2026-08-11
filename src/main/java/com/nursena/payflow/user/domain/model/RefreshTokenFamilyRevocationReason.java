package com.nursena.payflow.user.domain.model;

public enum RefreshTokenFamilyRevocationReason {
    CURRENT_SESSION_LOGOUT,
    ALL_SESSIONS_LOGOUT,
    REUSE_DETECTED,
    USER_ACCOUNT_UNAVAILABLE,
    PASSWORD_RECOVERY,
    MFA_DISABLED,
    ADMINISTRATIVE_REVOCATION
}

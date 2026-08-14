package com.nursena.payflow.abuseprotection.application.policy;

public enum AbuseProtectionWorkflow {
    REGISTRATION("registration"),
    EMAIL_VERIFICATION_REQUEST(
        "email-verification-request"
    ),
    PASSWORD_RECOVERY_REQUEST(
        "password-recovery-request"
    ),
    MFA_LOGIN_CHALLENGE_CONFIRMATION(
        "mfa-login-challenge-confirmation"
    ),
    STEP_UP_GRANT_ISSUANCE(
        "step-up-grant-issuance"
    );

    private final String configurationKey;

    AbuseProtectionWorkflow(
        String configurationKey
    ) {
        this.configurationKey = configurationKey;
    }

    public String configurationKey() {
        return configurationKey;
    }
}

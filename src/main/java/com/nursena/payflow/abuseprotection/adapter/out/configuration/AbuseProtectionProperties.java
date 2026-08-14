package com.nursena.payflow.abuseprotection.adapter.out.configuration;

import java.time.Duration;
import java.util.Objects;

import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionFailureMode;
import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionPolicy;
import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionWorkflow;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(
    prefix = "payflow.security.abuse-protection"
)
public record AbuseProtectionProperties(
    boolean enabled,
    WorkflowPolicyProperties registration,
    WorkflowPolicyProperties emailVerificationRequest,
    WorkflowPolicyProperties passwordRecoveryRequest,
    WorkflowPolicyProperties mfaLoginChallengeConfirmation,
    WorkflowPolicyProperties stepUpGrantIssuance
) {

    public AbuseProtectionProperties {
        Objects.requireNonNull(
            registration,
            "registration policy must not be null"
        );

        Objects.requireNonNull(
            emailVerificationRequest,
            "emailVerificationRequest policy must not be null"
        );

        Objects.requireNonNull(
            passwordRecoveryRequest,
            "passwordRecoveryRequest policy must not be null"
        );

        Objects.requireNonNull(
            mfaLoginChallengeConfirmation,
            "mfaLoginChallengeConfirmation policy must not be null"
        );

        Objects.requireNonNull(
            stepUpGrantIssuance,
            "stepUpGrantIssuance policy must not be null"
        );
    }

    AbuseProtectionPolicy policyFor(
        AbuseProtectionWorkflow workflow
    ) {
        Objects.requireNonNull(
            workflow,
            "workflow must not be null"
        );

        WorkflowPolicyProperties configuredPolicy =
            switch (workflow) {
                case REGISTRATION ->
                    registration;
                case EMAIL_VERIFICATION_REQUEST ->
                    emailVerificationRequest;
                case PASSWORD_RECOVERY_REQUEST ->
                    passwordRecoveryRequest;
                case MFA_LOGIN_CHALLENGE_CONFIRMATION ->
                    mfaLoginChallengeConfirmation;
                case STEP_UP_GRANT_ISSUANCE ->
                    stepUpGrantIssuance;
            };

        return configuredPolicy.toPolicy(enabled);
    }

    public record WorkflowPolicyProperties(
        boolean enabled,
        Duration window,
        int identityLimit,
        int clientLimit,
        AbuseProtectionFailureMode dependencyFailureMode
    ) {

        public WorkflowPolicyProperties {
            new AbuseProtectionPolicy(
                enabled,
                window,
                identityLimit,
                clientLimit,
                dependencyFailureMode
            );
        }

        AbuseProtectionPolicy toPolicy(
            boolean globallyEnabled
        ) {
            return new AbuseProtectionPolicy(
                globallyEnabled && enabled,
                window,
                identityLimit,
                clientLimit,
                dependencyFailureMode
            );
        }
    }
}

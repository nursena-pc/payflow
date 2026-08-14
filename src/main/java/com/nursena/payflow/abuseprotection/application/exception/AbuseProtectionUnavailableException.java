package com.nursena.payflow.abuseprotection.application.exception;

import java.util.Objects;

import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionFailureMode;
import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionWorkflow;

public final class AbuseProtectionUnavailableException
    extends RuntimeException {

    private final AbuseProtectionWorkflow workflow;
    private final AbuseProtectionFailureMode failureMode;

    public AbuseProtectionUnavailableException(
        AbuseProtectionWorkflow workflow,
        AbuseProtectionFailureMode failureMode,
        Throwable cause
    ) {
        super(
            "Abuse protection is temporarily unavailable.",
            Objects.requireNonNull(
                cause,
                "cause must not be null"
            )
        );

        this.workflow = Objects.requireNonNull(
            workflow,
            "workflow must not be null"
        );

        this.failureMode = Objects.requireNonNull(
            failureMode,
            "failureMode must not be null"
        );
    }

    public AbuseProtectionWorkflow workflow() {
        return workflow;
    }

    public AbuseProtectionFailureMode failureMode() {
        return failureMode;
    }
}

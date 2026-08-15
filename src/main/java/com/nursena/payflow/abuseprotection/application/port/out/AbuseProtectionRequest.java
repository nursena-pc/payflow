package com.nursena.payflow.abuseprotection.application.port.out;

import java.util.Objects;

import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionWorkflow;
import com.nursena.payflow.clientcontext.domain.IpAddress;

public record AbuseProtectionRequest(
    AbuseProtectionWorkflow workflow,
    String normalizedIdentity,
    IpAddress effectiveClientAddress
) {

    private static final int MAXIMUM_IDENTITY_LENGTH = 1024;

    public AbuseProtectionRequest {
        Objects.requireNonNull(
            workflow,
            "workflow must not be null"
        );

        Objects.requireNonNull(
            normalizedIdentity,
            "normalizedIdentity must not be null"
        );

        Objects.requireNonNull(
            effectiveClientAddress,
            "effectiveClientAddress must not be null"
        );

        if (
            normalizedIdentity.isBlank()
                || !normalizedIdentity.equals(
                    normalizedIdentity.trim()
                )
        ) {
            throw new IllegalArgumentException(
                "normalizedIdentity must be non-blank and trimmed"
            );
        }

        if (
            normalizedIdentity.length()
                > MAXIMUM_IDENTITY_LENGTH
        ) {
            throw new IllegalArgumentException(
                "normalizedIdentity must not exceed 1024 characters"
            );
        }
    }

    @Override
    public String toString() {
        return "AbuseProtectionRequest[redacted]";
    }
}

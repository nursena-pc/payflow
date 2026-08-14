package com.nursena.payflow.abuseprotection.adapter.out.configuration;

import java.util.Objects;

import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionPolicy;
import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionPolicyProvider;
import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionWorkflow;

final class ConfiguredAbuseProtectionPolicyProvider
    implements AbuseProtectionPolicyProvider {

    private final AbuseProtectionProperties properties;

    ConfiguredAbuseProtectionPolicyProvider(
        AbuseProtectionProperties properties
    ) {
        this.properties =
            Objects.requireNonNull(
                properties,
                "properties must not be null"
            );
    }

    @Override
    public AbuseProtectionPolicy policyFor(
        AbuseProtectionWorkflow workflow
    ) {
        return properties.policyFor(workflow);
    }
}

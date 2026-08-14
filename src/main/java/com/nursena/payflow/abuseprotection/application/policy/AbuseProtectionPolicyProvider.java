package com.nursena.payflow.abuseprotection.application.policy;

@FunctionalInterface
public interface AbuseProtectionPolicyProvider {

    AbuseProtectionPolicy policyFor(
        AbuseProtectionWorkflow workflow
    );
}

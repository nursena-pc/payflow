package com.nursena.payflow.abuseprotection.application.port.out;

@FunctionalInterface
public interface AbuseProtectionEnforcementPort {

    AbuseProtectionDecision evaluate(
        AbuseProtectionRequest request
    );
}

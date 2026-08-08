package com.nursena.payflow.user.adapter.out.security;

import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payflow.security.mfa.secret-protection")
public record MfaSecretProtectionProperties(
    MfaSecretProtectionMode providerMode,
    String keyBase64
) {
    public MfaSecretProtectionProperties {
        Objects.requireNonNull(
            providerMode,
            "providerMode must not be null"
        );
        keyBase64 = keyBase64 == null ? "" : keyBase64.trim();

        if (
            providerMode == MfaSecretProtectionMode.CONFIGURED
                && keyBase64.isBlank()
        ) {
            throw new IllegalArgumentException(
                "keyBase64 must be configured in configured mode"
            );
        }
    }
}

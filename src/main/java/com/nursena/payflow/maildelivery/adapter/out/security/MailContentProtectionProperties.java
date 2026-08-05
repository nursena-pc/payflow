package com.nursena.payflow.maildelivery.adapter.out.security;

import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payflow.mail.content-protection")
public record MailContentProtectionProperties(
    MailContentProtectionMode providerMode,
    String keyBase64
) {

    public MailContentProtectionProperties {
        Objects.requireNonNull(providerMode, "providerMode must not be null");
        keyBase64 = keyBase64 == null ? "" : keyBase64.trim();
        if (providerMode == MailContentProtectionMode.CONFIGURED
            && keyBase64.isBlank()) {
            throw new IllegalArgumentException(
                "keyBase64 must be configured in configured mode"
            );
        }
    }
}

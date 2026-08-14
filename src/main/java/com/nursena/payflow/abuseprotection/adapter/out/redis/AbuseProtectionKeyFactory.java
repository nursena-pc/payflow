package com.nursena.payflow.abuseprotection.adapter.out.redis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionWorkflow;

final class AbuseProtectionKeyFactory {

    private static final String KEY_PREFIX =
        "payflow:security:abuse:v1:";

    private static final String DIGEST_DOMAIN =
        "payflow-abuse-protection-v1";

    private AbuseProtectionKeyFactory() {
    }

    static String identityKey(
        AbuseProtectionWorkflow workflow,
        String normalizedIdentity
    ) {
        Objects.requireNonNull(
            normalizedIdentity,
            "normalizedIdentity must not be null"
        );

        return key(
            workflow,
            "identity",
            normalizedIdentity
        );
    }

    static String clientKey(
        AbuseProtectionWorkflow workflow,
        String effectiveClientAddress
    ) {
        Objects.requireNonNull(
            effectiveClientAddress,
            "effectiveClientAddress must not be null"
        );

        return key(
            workflow,
            "client",
            effectiveClientAddress
        );
    }

    private static String key(
        AbuseProtectionWorkflow workflow,
        String dimension,
        String sensitiveValue
    ) {
        AbuseProtectionWorkflow validatedWorkflow =
            Objects.requireNonNull(
                workflow,
                "workflow must not be null"
            );

        String digestInput =
            DIGEST_DOMAIN
                + "\u0000"
                + validatedWorkflow.configurationKey()
                + "\u0000"
                + dimension
                + "\u0000"
                + sensitiveValue;

        return KEY_PREFIX
            + validatedWorkflow.configurationKey()
            + ":"
            + dimension
            + ":"
            + sha256(digestInput);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest =
                MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(
                digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
                )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                "SHA-256 is not available",
                exception
            );
        }
    }
}

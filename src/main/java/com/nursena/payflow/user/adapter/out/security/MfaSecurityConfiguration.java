package com.nursena.payflow.user.adapter.out.security;

import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nursena.payflow.user.application.port.out.MfaRecoveryCodeDigestPort;
import com.nursena.payflow.user.application.port.out.MfaRecoveryCodeGenerationPort;
import com.nursena.payflow.user.application.port.out.MfaSecretProtectionPort;
import com.nursena.payflow.user.application.port.out.TotpSecretGenerationPort;
import com.nursena.payflow.user.application.port.out.TotpVerificationPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
    MfaSecretProtectionProperties.class,
    TotpProperties.class
})
class MfaSecurityConfiguration {

    private static final int KEY_LENGTH = 32;

    @Bean
    MfaSecretProtectionPort mfaSecretProtectionPort(
        MfaSecretProtectionProperties properties
    ) {
        byte[] keyBytes = properties.providerMode()
            == MfaSecretProtectionMode.EPHEMERAL
                ? randomKey()
                : decodeConfiguredKey(properties.keyBase64());

        SecretKey key = new SecretKeySpec(keyBytes, "AES");

        return new AesGcmMfaSecretProtectionAdapter(
            key,
            new SecureRandom()
        );
    }

    @Bean
    TotpSecretGenerationPort totpSecretGenerationPort() {
        return new SecureRandomTotpSecretGenerationAdapter(
            new SecureRandom()
        );
    }

    @Bean
    TotpVerificationPort totpVerificationPort() {
        return new HmacSha1TotpVerificationAdapter();
    }

    @Bean
    MfaRecoveryCodeGenerationPort mfaRecoveryCodeGenerationPort() {
        return new SecureRandomMfaRecoveryCodeGenerationAdapter(
            new SecureRandom()
        );
    }

    @Bean
    MfaRecoveryCodeDigestPort mfaRecoveryCodeDigestPort() {
        return new Sha256MfaRecoveryCodeDigestAdapter();
    }

    private static byte[] randomKey() {
        byte[] key = new byte[KEY_LENGTH];
        new SecureRandom().nextBytes(key);
        return key;
    }

    private static byte[] decodeConfiguredKey(String keyBase64) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(keyBase64);
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                "MFA secret-protection key must be valid Base64",
                exception
            );
        }

        if (key.length != KEY_LENGTH) {
            throw new IllegalArgumentException(
                "MFA secret-protection key must decode to 32 bytes"
            );
        }

        return key;
    }
}

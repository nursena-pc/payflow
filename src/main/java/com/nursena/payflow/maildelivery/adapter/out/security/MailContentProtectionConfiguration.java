package com.nursena.payflow.maildelivery.adapter.out.security;

import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nursena.payflow.maildelivery.application.port.out.MailContentProtectionPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MailContentProtectionProperties.class)
class MailContentProtectionConfiguration {

    private static final int KEY_LENGTH = 32;

    @Bean
    MailContentProtectionPort mailContentProtectionPort(
        MailContentProtectionProperties properties
    ) {
        byte[] keyBytes = properties.providerMode()
            == MailContentProtectionMode.EPHEMERAL
                ? randomKey()
                : decodeConfiguredKey(properties.keyBase64());
        SecretKey key = new SecretKeySpec(keyBytes, "AES");
        return new AesGcmMailContentProtectionAdapter(
            key,
            new SecureRandom()
        );
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
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                "mail content-protection key must be valid Base64",
                exception
            );
        }
        if (key.length != KEY_LENGTH) {
            throw new IllegalArgumentException(
                "mail content-protection key must decode to 32 bytes"
            );
        }
        return key;
    }
}

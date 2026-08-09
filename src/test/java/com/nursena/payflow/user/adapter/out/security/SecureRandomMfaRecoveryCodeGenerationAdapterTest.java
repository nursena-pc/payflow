package com.nursena.payflow.user.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SecureRandomMfaRecoveryCodeGenerationAdapterTest {

    @Test
    void shouldGenerateCanonical128BitBase64UrlCodes() {
        var adapter = new SecureRandomMfaRecoveryCodeGenerationAdapter(
            new SecureRandom()
        );

        Set<String> values = new HashSet<>();

        for (int index = 0; index < 64; index++) {
            String value = adapter.generate().value();
            assertThat(value)
                .hasSize(22)
                .matches("[A-Za-z0-9_-]{22}")
                .doesNotContain("=");
            values.add(value);
        }

        assertThat(values).hasSize(64);
    }

    @Test
    void shouldRedactGeneratedCodeToString() {
        var adapter = new SecureRandomMfaRecoveryCodeGenerationAdapter(
            new SecureRandom()
        );
        var generated = adapter.generate();

        assertThat(generated.toString())
            .doesNotContain(generated.value())
            .containsIgnoringCase("redacted");
    }
}

package com.nursena.payflow.user.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;

import com.nursena.payflow.user.application.port.out.GeneratedTotpSecret;
import org.junit.jupiter.api.Test;

class SecureRandomTotpSecretGenerationAdapterTest {

    @Test
    void shouldGenerateTwentyByteSecret() {
        GeneratedTotpSecret generated = adapter().generate();
        assertThat(generated.value()).hasSize(20);
    }

    @Test
    void shouldGenerateCanonicalUnpaddedBase32() {
        GeneratedTotpSecret generated = adapter().generate();
        assertThat(generated.base32()).matches("[A-Z2-7]{32}");
    }

    @Test
    void shouldGenerateDistinctSecrets() {
        SecureRandomTotpSecretGenerationAdapter adapter = adapter();
        assertThat(adapter.generate().value()).isNotEqualTo(adapter.generate().value());
    }

    private static SecureRandomTotpSecretGenerationAdapter adapter() {
        return new SecureRandomTotpSecretGenerationAdapter(new SecureRandom());
    }
}

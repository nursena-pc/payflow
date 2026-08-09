package com.nursena.payflow.user.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import com.nursena.payflow.user.application.port.out.GeneratedMfaLoginChallenge;
import org.junit.jupiter.api.Test;

class SecureRandomMfaLoginChallengeGenerationAdapterTest {

    @Test
    void shouldGenerateCanonicalUnpaddedBase64UrlCredential() {
        GeneratedMfaLoginChallenge generated =
            new SecureRandomMfaLoginChallengeGenerationAdapter(new SecureRandom()).generate();
        assertThat(generated.value())
            .hasSize(43)
            .matches("[A-Za-z0-9_-]{43}")
            .doesNotContain("=");
    }

    @Test
    void shouldGenerateDistinctValues() {
        SecureRandomMfaLoginChallengeGenerationAdapter adapter =
            new SecureRandomMfaLoginChallengeGenerationAdapter(new SecureRandom());
        assertThat(adapter.generate().value()).isNotEqualTo(adapter.generate().value());
    }

    @Test
    void shouldRedactGeneratedCredential() {
        GeneratedMfaLoginChallenge generated =
            new SecureRandomMfaLoginChallengeGenerationAdapter(new SecureRandom()).generate();
        assertThat(generated.toString()).doesNotContain(generated.value());
    }
}

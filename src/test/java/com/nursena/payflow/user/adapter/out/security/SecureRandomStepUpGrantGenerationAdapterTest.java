package com.nursena.payflow.user.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.Base64;
import com.nursena.payflow.user.application.port.out.GeneratedStepUpGrant;
import org.junit.jupiter.api.Test;

class SecureRandomStepUpGrantGenerationAdapterTest {

    @Test
    void shouldGenerateCanonicalUnpaddedBase64UrlWithTwoHundredFiftySixBits() {
        GeneratedStepUpGrant generated =
            new SecureRandomStepUpGrantGenerationAdapter(new SecureRandom()).generate();
        assertThat(generated.value()).matches("[A-Za-z0-9_-]{43}");
        assertThat(Base64.getUrlDecoder().decode(generated.value())).hasSize(32);
    }

    @Test
    void shouldGenerateDifferentCredentialsAndRedactToString() {
        SecureRandomStepUpGrantGenerationAdapter adapter =
            new SecureRandomStepUpGrantGenerationAdapter(new SecureRandom());
        GeneratedStepUpGrant first = adapter.generate();
        GeneratedStepUpGrant second = adapter.generate();
        assertThat(first.value()).isNotEqualTo(second.value());
        assertThat(first.toString()).doesNotContain(first.value());
    }
}

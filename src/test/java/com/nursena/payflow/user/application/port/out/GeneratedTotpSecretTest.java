package com.nursena.payflow.user.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GeneratedTotpSecretTest {

    @Test
    void shouldDefensivelyCopySecretBytes() {
        byte[] bytes = new byte[20];
        bytes[0] = 1;
        GeneratedTotpSecret secret = new GeneratedTotpSecret(
            bytes,
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        );
        bytes[0] = 2;
        byte[] exposed = secret.value();
        exposed[0] = 3;
        assertThat(secret.value()[0]).isEqualTo((byte) 1);
    }

    @Test
    void shouldRedactStringRepresentation() {
        GeneratedTotpSecret secret = new GeneratedTotpSecret(
            new byte[20],
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        );
        assertThat(secret.toString()).isEqualTo("GeneratedTotpSecret[REDACTED]");
    }

    @Test
    void shouldRejectShortSecret() {
        assertThatThrownBy(() -> new GeneratedTotpSecret(
            new byte[19],
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        )).isInstanceOf(IllegalArgumentException.class);
    }
}

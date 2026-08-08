package com.nursena.payflow.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProtectedMfaSecretTest {

    @Test
    void shouldDefensivelyCopyProtectedBytes() {
        byte[] source = {1, 2, 3};
        ProtectedMfaSecret secret = ProtectedMfaSecret.of(source);
        source[0] = 9;
        byte[] firstRead = secret.value();
        firstRead[1] = 9;
        assertThat(secret.value()).containsExactly(1, 2, 3);
    }

    @Test
    void shouldRedactStringRepresentation() {
        assertThat(ProtectedMfaSecret.of(new byte[] {1}).toString())
            .isEqualTo("ProtectedMfaSecret[REDACTED]");
    }

    @Test
    void shouldRejectEmptyProtectedValue() {
        assertThatThrownBy(() -> ProtectedMfaSecret.of(new byte[0]))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

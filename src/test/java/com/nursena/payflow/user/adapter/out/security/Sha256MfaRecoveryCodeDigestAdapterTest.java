package com.nursena.payflow.user.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class Sha256MfaRecoveryCodeDigestAdapterTest {

    private final Sha256MfaRecoveryCodeDigestAdapter adapter =
        new Sha256MfaRecoveryCodeDigestAdapter();

    @Test
    void shouldCreateStableFixedLengthDigest() {
        String code = "AbCdEfGhIjKlMnOpQrStUv";

        assertThat(adapter.digest(code).value()).hasSize(32);
        assertThat(adapter.digest(code))
            .isEqualTo(adapter.digest(code));
    }

    @Test
    void shouldRejectNonCanonicalRecoveryCodeShape() {
        assertThatThrownBy(() -> adapter.digest("123456"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.digest(
            "AbCdEfGhIjKlMnOpQrStUv="
        )).isInstanceOf(IllegalArgumentException.class);
    }
}

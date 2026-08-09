package com.nursena.payflow.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MfaRecoveryCodeDigestTest {

    @Test
    void shouldRequireExactlySha256Length() {
        assertThatThrownBy(() -> MfaRecoveryCodeDigest.of(new byte[31]))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MfaRecoveryCodeDigest.of(new byte[33]))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldDefensivelyCopyDigestBytes() {
        byte[] source = new byte[32];
        source[0] = 7;
        MfaRecoveryCodeDigest digest = MfaRecoveryCodeDigest.of(source);
        source[0] = 9;

        byte[] returned = digest.value();
        assertThat(returned[0]).isEqualTo((byte) 7);
        returned[0] = 11;
        assertThat(digest.value()[0]).isEqualTo((byte) 7);
    }

    @Test
    void shouldUseValueEqualityAndRedactedString() {
        byte[] value = new byte[32];
        value[4] = 3;

        assertThat(MfaRecoveryCodeDigest.of(value))
            .isEqualTo(MfaRecoveryCodeDigest.of(value))
            .hasSameHashCodeAs(MfaRecoveryCodeDigest.of(value));
        assertThat(MfaRecoveryCodeDigest.of(value).toString())
            .isEqualTo("MfaRecoveryCodeDigest[redacted]");
    }
}

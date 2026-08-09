package com.nursena.payflow.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MfaLoginChallengeDigestTest {

    @Test
    void shouldRequireExactlySha256Length() {
        assertThatThrownBy(() -> MfaLoginChallengeDigest.of(new byte[31]))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldDefensivelyCopyInput() {
        byte[] bytes = new byte[32];
        MfaLoginChallengeDigest digest = MfaLoginChallengeDigest.of(bytes);
        bytes[0] = 9;
        assertThat(digest.value()[0]).isZero();
    }

    @Test
    void shouldDefensivelyCopyOutput() {
        MfaLoginChallengeDigest digest = MfaLoginChallengeDigest.of(new byte[32]);
        byte[] exposed = digest.value();
        exposed[0] = 9;
        assertThat(digest.value()[0]).isZero();
    }

    @Test
    void shouldRedactToString() {
        assertThat(MfaLoginChallengeDigest.of(new byte[32]).toString())
            .isEqualTo("MfaLoginChallengeDigest[redacted]");
    }
}

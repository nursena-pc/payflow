package com.nursena.payflow.user.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;

class Sha256MfaLoginChallengeDigestAdapterTest {

    @Test
    void shouldDigestChallengeWithSha256() throws Exception {
        String value = "challenge-token";
        byte[] expected = MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.US_ASCII));
        assertThat(new Sha256MfaLoginChallengeDigestAdapter().digest(value).value())
            .containsExactly(expected);
    }

    @Test
    void shouldRejectBlankChallenge() {
        assertThatThrownBy(() -> new Sha256MfaLoginChallengeDigestAdapter().digest(" "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectOversizedChallenge() {
        assertThatThrownBy(() -> new Sha256MfaLoginChallengeDigestAdapter().digest("x".repeat(257)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

package com.nursena.payflow.user.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;

class Sha256StepUpGrantDigestAdapterTest {

    @Test
    void shouldDigestGrantWithSha256() throws Exception {
        String token = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ";
        byte[] expected = MessageDigest.getInstance("SHA-256")
            .digest(token.getBytes(StandardCharsets.US_ASCII));
        assertThat(new Sha256StepUpGrantDigestAdapter().digest(token).value())
            .containsExactly(expected);
    }

    @Test
    void shouldRejectBlankOrOversizedInputWithoutExposingIt() {
        Sha256StepUpGrantDigestAdapter adapter = new Sha256StepUpGrantDigestAdapter();
        assertThatThrownBy(() -> adapter.digest(""))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.digest("x".repeat(257)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

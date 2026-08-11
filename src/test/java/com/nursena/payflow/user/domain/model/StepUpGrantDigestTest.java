package com.nursena.payflow.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class StepUpGrantDigestTest {

    @Test
    void shouldRequireExactlyThirtyTwoBytes() {
        assertThatThrownBy(() -> StepUpGrantDigest.of(null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StepUpGrantDigest.of(new byte[31]))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StepUpGrantDigest.of(new byte[33]))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldDefensivelyCopyInputAndOutput() {
        byte[] source = new byte[32];
        source[0] = 7;
        StepUpGrantDigest digest = StepUpGrantDigest.of(source);
        source[0] = 9;
        byte[] exposed = digest.value();
        exposed[0] = 4;
        assertThat(digest.value()[0]).isEqualTo((byte) 7);
    }

    @Test
    void shouldUseValueEqualityAndRedactedToString() {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) 5);
        StepUpGrantDigest first = StepUpGrantDigest.of(bytes);
        StepUpGrantDigest second = StepUpGrantDigest.of(bytes);
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        assertThat(first.toString()).isEqualTo("StepUpGrantDigest[redacted]");
    }
}

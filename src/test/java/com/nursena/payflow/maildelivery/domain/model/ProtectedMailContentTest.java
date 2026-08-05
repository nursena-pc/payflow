package com.nursena.payflow.maildelivery.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProtectedMailContentTest {

    @Test
    void shouldDefensivelyCopyAndRedactContent() {
        byte[] source = {1, 2, 3, 4};
        ProtectedMailContent content = ProtectedMailContent.of(source);
        source[0] = 99;
        byte[] exposed = content.value();
        exposed[1] = 88;

        assertThat(content.value()).containsExactly(1, 2, 3, 4);
        assertThat(content.toString())
            .isEqualTo("ProtectedMailContent[redacted]")
            .doesNotContain("1", "2", "3", "4");
    }
}

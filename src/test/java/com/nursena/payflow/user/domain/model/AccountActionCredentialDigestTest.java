package com.nursena.payflow.user.domain.model;

import static org.assertj.core.api.Assertions
    .assertThat;
import static org.assertj.core.api.Assertions
    .assertThatThrownBy;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class AccountActionCredentialDigestTest {

    @Test
    void shouldDefensivelyCopyDigestBytes() {
        byte[] source = new byte[
            AccountActionCredentialDigest
                .SHA_256_LENGTH_BYTES
        ];
        source[0] = 7;

        AccountActionCredentialDigest digest =
            AccountActionCredentialDigest.of(source);

        source[0] = 9;
        byte[] exposed = digest.value();
        exposed[0] = 11;

        assertThat(digest.value()[0])
            .isEqualTo((byte) 7);
        assertThat(digest.toString())
            .isEqualTo(
                "AccountActionCredentialDigest[redacted]"
            );
    }

    @Test
    void shouldCompareByDigestBytes() {
        byte[] value = new byte[
            AccountActionCredentialDigest
                .SHA_256_LENGTH_BYTES
        ];
        Arrays.fill(value, (byte) 3);

        AccountActionCredentialDigest first =
            AccountActionCredentialDigest.of(value);
        AccountActionCredentialDigest second =
            AccountActionCredentialDigest.of(value);

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode())
            .isEqualTo(second.hashCode());
    }

    @Test
    void shouldRequireExactSha256Length() {
        assertThatThrownBy(() ->
            AccountActionCredentialDigest.of(
                new byte[31]
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "value must contain exactly 32 bytes"
            );
    }
}

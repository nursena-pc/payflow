package com.nursena.payflow.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class RefreshTokenDigestTest {

    @Test
    void shouldDefensivelyCopyDigestBytes() {
        byte[] source = digestBytes((byte) 7);

        RefreshTokenDigest digest =
            RefreshTokenDigest.of(source);

        source[0] = 99;

        byte[] exposed = digest.value();
        exposed[1] = 88;

        assertThat(digest.value())
            .containsOnly((byte) 7);
    }

    @Test
    void shouldCompareByDigestContent() {
        RefreshTokenDigest first =
            RefreshTokenDigest.of(
                digestBytes((byte) 4)
            );

        RefreshTokenDigest second =
            RefreshTokenDigest.of(
                digestBytes((byte) 4)
            );

        assertThat(first)
            .isEqualTo(second);

        assertThat(first.hashCode())
            .isEqualTo(second.hashCode());
    }

    @Test
    void shouldRequireSha256Length() {
        assertThatThrownBy(() ->
            RefreshTokenDigest.of(
                new byte[31]
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "value must contain exactly 32 bytes"
            );

        assertThatThrownBy(() ->
            RefreshTokenDigest.of(
                new byte[33]
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "value must contain exactly 32 bytes"
            );
    }

    @Test
    void shouldRedactDigestFromStringRepresentation() {
        RefreshTokenDigest digest =
            RefreshTokenDigest.of(
                digestBytes((byte) 12)
            );

        assertThat(digest.toString())
            .isEqualTo(
                "RefreshTokenDigest[redacted]"
            );

        assertThat(digest.toString())
            .doesNotContain(
                Arrays.toString(
                    digest.value()
                )
            );
    }

    private static byte[] digestBytes(
        byte value
    ) {
        byte[] bytes =
            new byte[
                RefreshTokenDigest
                    .SHA_256_LENGTH_BYTES
            ];

        Arrays.fill(bytes, value);

        return bytes;
    }
}

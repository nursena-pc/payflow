package com.nursena.payflow.user.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.HexFormat;
import java.util.stream.Stream;

import com.nursena.payflow.user.domain.exception.InvalidRefreshTokenException;
import com.nursena.payflow.user.domain.model.RefreshTokenDigest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class Sha256RefreshTokenDigestAdapterTest {

    private static final String CANONICAL_TOKEN =
        "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";

    private static final byte[] EXPECTED_DIGEST =
        HexFormat.of().parseHex(
            "630dcd2966c4336691125448bbb25b4f"
                + "f412a49c732db2c8abc1b8581bd710dd"
        );

    private final Sha256RefreshTokenDigestAdapter
        adapter =
        new Sha256RefreshTokenDigestAdapter();

    @Test
    void shouldCalculateSha256OverDecodedTokenBytes() {
        RefreshTokenDigest result =
            adapter.digest(
                CANONICAL_TOKEN
            );

        assertThat(result.value())
            .containsExactly(
                EXPECTED_DIGEST
            );

        assertThat(result.value())
            .hasSize(
                RefreshTokenDigest
                    .SHA_256_LENGTH_BYTES
            );

        assertThat(result.toString())
            .isEqualTo(
                "RefreshTokenDigest[redacted]"
            );
    }

    @Test
    void shouldProduceDeterministicDigest() {
        RefreshTokenDigest first =
            adapter.digest(
                CANONICAL_TOKEN
            );

        RefreshTokenDigest second =
            adapter.digest(
                CANONICAL_TOKEN
            );

        assertThat(first)
            .isEqualTo(second);

        assertThat(first.hashCode())
            .isEqualTo(
                second.hashCode()
            );
    }

    @Test
    void shouldRejectNullTokenWithoutExposingCredentialState() {
        Throwable failure =
            catchThrowable(() ->
                adapter.digest(null)
            );

        assertThat(failure)
            .isInstanceOf(
                InvalidRefreshTokenException.class
            )
            .hasMessage(
                "Refresh token is invalid."
            );
    }

    @ParameterizedTest
    @MethodSource("invalidTokens")
    void shouldRejectMalformedOrNonCanonicalTokens(
        String invalidToken
    ) {
        Throwable failure =
            catchThrowable(() ->
                adapter.digest(
                    invalidToken
                )
            );

        assertThat(failure)
            .isInstanceOf(
                InvalidRefreshTokenException.class
            )
            .hasMessage(
                "Refresh token is invalid."
            );

        assertThat(failure.getCause())
            .isNull();

        if (!invalidToken.isBlank()) {
            assertThat(failure.toString())
                .doesNotContain(
                    invalidToken
                );
        }
    }

    private static Stream<String> invalidTokens() {
        return Stream.of(
            "",
            " ",
            CANONICAL_TOKEN.substring(
                0,
                CANONICAL_TOKEN.length() - 1
            ),
            CANONICAL_TOKEN + "A",
            CANONICAL_TOKEN.substring(0, 42) + "=",
            CANONICAL_TOKEN.substring(0, 42) + "+",
            CANONICAL_TOKEN.substring(0, 42) + "/",
            " " + CANONICAL_TOKEN.substring(1),
            CANONICAL_TOKEN.substring(0, 42) + "9"
        );
    }
}

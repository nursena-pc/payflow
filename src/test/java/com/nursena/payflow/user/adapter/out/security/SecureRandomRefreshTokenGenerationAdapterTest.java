package com.nursena.payflow.user.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.security.SecureRandom;
import java.util.Base64;

import com.nursena.payflow.user.application.port.out.GeneratedRefreshToken;
import org.junit.jupiter.api.Test;

class SecureRandomRefreshTokenGenerationAdapterTest {

    private static final String EXPECTED_TOKEN =
        "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";

    @Test
    void shouldGenerateExpectedCanonicalTokenFrom32Bytes() {
        byte[] deterministicEntropy =
            new byte[
                SecureRandomRefreshTokenGenerationAdapter
                    .ENTROPY_LENGTH_BYTES
            ];

        for (
            int index = 0;
            index < deterministicEntropy.length;
            index++
        ) {
            deterministicEntropy[index] =
                (byte) index;
        }

        SecureRandom secureRandom =
            mock(SecureRandom.class);

        doAnswer(invocation -> {
            byte[] destination =
                invocation.getArgument(0);

            assertThat(destination)
                .hasSize(
                    SecureRandomRefreshTokenGenerationAdapter
                        .ENTROPY_LENGTH_BYTES
                );

            System.arraycopy(
                deterministicEntropy,
                0,
                destination,
                0,
                deterministicEntropy.length
            );

            return null;
        })
            .when(secureRandom)
            .nextBytes(
                any(byte[].class)
            );

        SecureRandomRefreshTokenGenerationAdapter
            adapter =
            new SecureRandomRefreshTokenGenerationAdapter(
                secureRandom
            );

        GeneratedRefreshToken result =
            adapter.generate();

        assertThat(result.value())
            .isEqualTo(EXPECTED_TOKEN);

        assertThat(result.value())
            .hasSize(
                SecureRandomRefreshTokenGenerationAdapter
                    .ENCODED_TOKEN_LENGTH
            );

        assertThat(
            Base64.getUrlDecoder()
                .decode(result.value())
        )
            .containsExactly(
                deterministicEntropy
            );

        assertThat(result.toString())
            .doesNotContain(
                result.value()
            );

        verify(secureRandom)
            .nextBytes(
                any(byte[].class)
            );
    }

    @Test
    void shouldGenerateCanonicalBase64UrlTokens() {
        SecureRandomRefreshTokenGenerationAdapter
            adapter =
            new SecureRandomRefreshTokenGenerationAdapter(
                new SecureRandom()
            );

        for (
            int attempt = 0;
            attempt < 64;
            attempt++
        ) {
            GeneratedRefreshToken generated =
                adapter.generate();

            assertThat(generated.value())
                .hasSize(
                    SecureRandomRefreshTokenGenerationAdapter
                        .ENCODED_TOKEN_LENGTH
                )
                .matches(
                    "[A-Za-z0-9_-]{43}"
                )
                .doesNotContain("=");

            assertThat(
                Base64.getUrlDecoder()
                    .decode(
                        generated.value()
                    )
            )
                .hasSize(
                    SecureRandomRefreshTokenGenerationAdapter
                        .ENTROPY_LENGTH_BYTES
                );
        }
    }

    @Test
    void shouldRequireSecureRandom() {
        assertThatThrownBy(() ->
            new SecureRandomRefreshTokenGenerationAdapter(
                null
            )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "secureRandom must not be null"
            );
    }
}

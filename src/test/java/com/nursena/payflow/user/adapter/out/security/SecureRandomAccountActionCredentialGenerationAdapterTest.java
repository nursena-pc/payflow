package com.nursena.payflow.user.adapter.out.security;

import static org.assertj.core.api.Assertions
    .assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.security.SecureRandom;
import java.util.Base64;

import com.nursena.payflow.user.application.port.out
    .GeneratedAccountActionCredential;
import org.junit.jupiter.api.Test;

class SecureRandomAccountActionCredentialGenerationAdapterTest {

    private static final String EXPECTED_CREDENTIAL =
        "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";

    @Test
    void shouldGenerateCanonicalCredentialFrom256Bits() {
        byte[] deterministicEntropy =
            new byte[
                SecureRandomAccountActionCredentialGenerationAdapter
                    .ENTROPY_LENGTH_BYTES
            ];

        for (
            int index = 0;
            index < deterministicEntropy.length;
            index++
        ) {
            deterministicEntropy[index] = (byte) index;
        }

        SecureRandom secureRandom =
            mock(SecureRandom.class);

        doAnswer(invocation -> {
            byte[] destination =
                invocation.getArgument(0);
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
            .nextBytes(any(byte[].class));

        SecureRandomAccountActionCredentialGenerationAdapter
            adapter =
            new SecureRandomAccountActionCredentialGenerationAdapter(
                secureRandom
            );

        GeneratedAccountActionCredential result =
            adapter.generate();

        assertThat(result.value())
            .isEqualTo(EXPECTED_CREDENTIAL)
            .matches("[A-Za-z0-9_-]{43}")
            .doesNotContain("=");
        assertThat(
            Base64.getUrlDecoder()
                .decode(result.value())
        )
            .containsExactly(deterministicEntropy);
        assertThat(result.toString())
            .doesNotContain(result.value());

        verify(secureRandom)
            .nextBytes(any(byte[].class));
    }
}

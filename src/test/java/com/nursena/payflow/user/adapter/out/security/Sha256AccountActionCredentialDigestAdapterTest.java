package com.nursena.payflow.user.adapter.out.security;

import static org.assertj.core.api.Assertions
    .assertThat;
import static org.assertj.core.api.Assertions
    .catchThrowable;

import java.util.HexFormat;
import java.util.stream.Stream;

import com.nursena.payflow.user.domain.exception
    .InvalidAccountActionCredentialException;
import com.nursena.payflow.user.domain.model
    .AccountActionCredentialDigest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class Sha256AccountActionCredentialDigestAdapterTest {

    private static final String CANONICAL_CREDENTIAL =
        "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";

    private static final byte[] EXPECTED_DIGEST =
        HexFormat.of().parseHex(
            "630dcd2966c4336691125448bbb25b4f"
                + "f412a49c732db2c8abc1b8581bd710dd"
        );

    private final Sha256AccountActionCredentialDigestAdapter
        adapter =
        new Sha256AccountActionCredentialDigestAdapter();

    @Test
    void shouldDigestDecodedCredentialBytes() {
        AccountActionCredentialDigest result =
            adapter.digest(CANONICAL_CREDENTIAL);

        assertThat(result.value())
            .containsExactly(EXPECTED_DIGEST);
        assertThat(result.toString())
            .isEqualTo(
                "AccountActionCredentialDigest[redacted]"
            );
    }

    @ParameterizedTest
    @MethodSource("invalidCredentials")
    void shouldRejectMalformedCredentialGenerically(
        String invalidCredential
    ) {
        Throwable failure = catchThrowable(() ->
            adapter.digest(invalidCredential)
        );

        assertThat(failure)
            .isInstanceOf(
                InvalidAccountActionCredentialException.class
            )
            .hasMessage(
                "Account action credential is invalid."
            );
        assertThat(failure.getCause()).isNull();

        if (
            invalidCredential != null
                && !invalidCredential.isBlank()
        ) {
            assertThat(failure.toString())
                .doesNotContain(invalidCredential);
        }
    }

    private static Stream<String> invalidCredentials() {
        return Stream.of(
            null,
            "",
            " ",
            CANONICAL_CREDENTIAL.substring(0, 42),
            CANONICAL_CREDENTIAL + "A",
            CANONICAL_CREDENTIAL.substring(0, 42) + "=",
            CANONICAL_CREDENTIAL.substring(0, 42) + "+",
            CANONICAL_CREDENTIAL.substring(0, 42) + "/",
            CANONICAL_CREDENTIAL.substring(0, 42) + "9"
        );
    }
}

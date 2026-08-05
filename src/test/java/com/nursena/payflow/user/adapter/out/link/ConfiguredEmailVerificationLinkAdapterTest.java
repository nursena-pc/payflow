package com.nursena.payflow.user.adapter.out.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;

import com.nursena.payflow.user.domain.exception
    .InvalidAccountActionCredentialException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfiguredEmailVerificationLinkAdapterTest {

    private static final String CREDENTIAL =
        "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";

    private ConfiguredEmailVerificationLinkAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ConfiguredEmailVerificationLinkAdapter(
            URI.create(
                "https://app.payflow.local/verify-email"
            )
        );
    }

    @Test
    void shouldAppendCredentialToConfiguredUriOnly() {
        assertThat(adapter.build(CREDENTIAL))
            .isEqualTo(
                URI.create(
                    "https://app.payflow.local/verify-email"
                        + "?token=" + CREDENTIAL
                )
            );
    }

    @Test
    void shouldRejectBlankCredential() {
        assertThatThrownBy(() -> adapter.build(" "))
            .isInstanceOf(
                InvalidAccountActionCredentialException.class
            );
    }
}

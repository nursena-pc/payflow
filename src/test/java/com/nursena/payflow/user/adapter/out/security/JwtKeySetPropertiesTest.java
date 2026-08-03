package com.nursena.payflow.user.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtKeySetPropertiesTest {

    @Test
    void shouldAcceptEphemeralDevelopmentConfiguration() {
        JwtKeySetProperties properties =
            new JwtKeySetProperties(
                JwtKeyProviderMode.EPHEMERAL,
                "local-development",
                "",
                "",
                "",
                ""
            );

        assertThat(properties.providerMode())
            .isEqualTo(JwtKeyProviderMode.EPHEMERAL);

        assertThat(properties.activeKeyId())
            .isEqualTo("local-development");

        assertThat(properties.hasPreviousKey())
            .isFalse();
    }

    @Test
    void shouldRequireConfiguredActiveKeyLocations() {
        assertThatThrownBy(
            () -> new JwtKeySetProperties(
                JwtKeyProviderMode.CONFIGURED,
                "active-2026-08",
                "",
                "file:/keys/active-public.pem",
                "",
                ""
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(
                "private-key location is required"
            );
    }

    @Test
    void shouldRequireCompletePreviousKeyConfiguration() {
        assertThatThrownBy(
            () -> new JwtKeySetProperties(
                JwtKeyProviderMode.CONFIGURED,
                "active-2026-08",
                "file:/keys/active-private.pem",
                "file:/keys/active-public.pem",
                "previous-2026-07",
                ""
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(
                "must be configured together"
            );
    }

    @Test
    void shouldRejectDuplicateActiveAndPreviousKeyIds() {
        assertThatThrownBy(
            () -> new JwtKeySetProperties(
                JwtKeyProviderMode.CONFIGURED,
                "rotation-key",
                "file:/keys/active-private.pem",
                "file:/keys/active-public.pem",
                "rotation-key",
                "file:/keys/previous-public.pem"
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must differ");
    }

    @Test
    void shouldRejectUnsafeKeyIdCharacters() {
        assertThatThrownBy(
            () -> new JwtKeySetProperties(
                JwtKeyProviderMode.EPHEMERAL,
                "../../private-key",
                "",
                "",
                "",
                ""
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("JWT key ID");
    }

    @Test
    void shouldRejectMisleadingKeyLocationsInEphemeralMode() {
        assertThatThrownBy(
            () -> new JwtKeySetProperties(
                JwtKeyProviderMode.EPHEMERAL,
                "local-development",
                "file:/keys/private.pem",
                "file:/keys/public.pem",
                "",
                ""
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(
                "must not declare key locations"
            );
    }
}

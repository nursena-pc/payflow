package com.nursena.payflow.user.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MfaSecretProtectionPropertiesTest {

    @Test
    void shouldAllowEphemeralModeWithoutConfiguredKey() {
        MfaSecretProtectionProperties properties =
            new MfaSecretProtectionProperties(MfaSecretProtectionMode.EPHEMERAL, "");
        assertThat(properties.providerMode()).isEqualTo(MfaSecretProtectionMode.EPHEMERAL);
    }

    @Test
    void shouldRetainConfiguredKey() {
        MfaSecretProtectionProperties properties =
            new MfaSecretProtectionProperties(MfaSecretProtectionMode.CONFIGURED, " key ");
        assertThat(properties.keyBase64()).isEqualTo("key");
    }

    @Test
    void shouldRejectConfiguredModeWithoutKey() {
        assertThatThrownBy(() -> new MfaSecretProtectionProperties(
            MfaSecretProtectionMode.CONFIGURED,
            " "
        )).isInstanceOf(IllegalArgumentException.class);
    }
}

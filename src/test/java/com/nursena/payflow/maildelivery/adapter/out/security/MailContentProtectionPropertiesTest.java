package com.nursena.payflow.maildelivery.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MailContentProtectionPropertiesTest {

    @Test
    void shouldRequireConfiguredKeyInConfiguredMode() {
        assertThatThrownBy(() -> new MailContentProtectionProperties(
            MailContentProtectionMode.CONFIGURED,
            ""
        )).isInstanceOf(IllegalArgumentException.class);

        MailContentProtectionProperties ephemeral =
            new MailContentProtectionProperties(
                MailContentProtectionMode.EPHEMERAL,
                ""
            );
        assertThat(ephemeral.providerMode())
            .isEqualTo(MailContentProtectionMode.EPHEMERAL);
    }
}

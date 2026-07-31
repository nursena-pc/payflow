package com.nursena.payflow.clientcontext.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class TrustedProxyPropertiesTest {

    @Test
    void shouldNormalizeTrustedProxyNetworks() {
        TrustedProxyProperties properties =
            new TrustedProxyProperties(
                List.of(
                    "10.0.0.0/8",
                    "2001:0db8:0:0:0:0:0:0/32"
                ),
                4096,
                16
            );

        assertThat(properties.trustedProxyCidrs())
            .containsExactly(
                "10.0.0.0/8",
                "2001:db8::/32"
            );

        assertThat(properties.trustedProxyNetworks())
            .extracting(
                network -> network.value()
            )
            .containsExactly(
                "10.0.0.0/8",
                "2001:db8::/32"
            );
    }

    @Test
    void shouldIgnoreBlankListEntries() {
        TrustedProxyProperties properties =
            new TrustedProxyProperties(
                List.of(
                    "",
                    "   ",
                    "127.0.0.1/32"
                ),
                4096,
                16
            );

        assertThat(properties.trustedProxyCidrs())
            .containsExactly(
                "127.0.0.1/32"
            );
    }

    @Test
    void shouldRejectDuplicateCanonicalNetwork() {
        assertThatThrownBy(
            () -> new TrustedProxyProperties(
                List.of(
                    "2001:db8::/32",
                    "2001:0db8:0:0:0:0:0:0/32"
                ),
                4096,
                16
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessageContaining(
                "duplicate"
            );
    }

    @Test
    void shouldRejectInvalidAndAllAddressNetworks() {
        assertThatThrownBy(
            () -> new TrustedProxyProperties(
                List.of(
                    "proxy.internal/24"
                ),
                4096,
                16
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            );

        assertThatThrownBy(
            () -> new TrustedProxyProperties(
                List.of(
                    "0.0.0.0/0"
                ),
                4096,
                16
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessageContaining(
                "must not trust all"
            );
    }

    @Test
    void shouldRejectExcessiveTrustedNetworkCount() {
        List<String> networks =
            new ArrayList<>();

        for (
            int index = 0;
            index < 65;
            index++
        ) {
            networks.add(
                "10."
                    + index
                    + ".0.0/16"
            );
        }

        assertThatThrownBy(
            () -> new TrustedProxyProperties(
                networks,
                4096,
                16
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessageContaining(
                "must not exceed 64"
            );
    }

    @Test
    void shouldValidateForwardedHeaderLengthBounds() {
        assertThatThrownBy(
            () -> new TrustedProxyProperties(
                List.of(),
                255,
                16
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessageContaining(
                "maxForwardedHeaderLength"
            );

        assertThatThrownBy(
            () -> new TrustedProxyProperties(
                List.of(),
                16_385,
                16
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessageContaining(
                "maxForwardedHeaderLength"
            );
    }

    @Test
    void shouldValidateForwardedHopBounds() {
        assertThatThrownBy(
            () -> new TrustedProxyProperties(
                List.of(),
                4096,
                0
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessageContaining(
                "maxForwardedHops"
            );

        assertThatThrownBy(
            () -> new TrustedProxyProperties(
                List.of(),
                4096,
                65
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessageContaining(
                "maxForwardedHops"
            );
    }

    @Test
    void shouldExposeImmutableConfiguration() {
        TrustedProxyProperties properties =
            new TrustedProxyProperties(
                List.of(
                    "10.0.0.0/8"
                ),
                4096,
                16
            );

        assertThatThrownBy(
            () -> properties
                .trustedProxyCidrs()
                .add(
                    "192.168.0.0/16"
                )
        )
            .isInstanceOf(
                UnsupportedOperationException.class
            );
    }
}

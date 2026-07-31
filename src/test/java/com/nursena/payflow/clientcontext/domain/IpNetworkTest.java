package com.nursena.payflow.clientcontext.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IpNetworkTest {

    @Test
    void shouldParseCanonicalNetwork() {
        IpNetwork network =
            IpNetwork.parse(
                "10.20.0.0/16"
            );

        assertThat(network.value())
            .isEqualTo("10.20.0.0/16");

        assertThat(network.prefixLength())
            .isEqualTo(16);
    }

    @Test
    void shouldMatchIpv4AddressInsideNetwork() {
        IpNetwork network =
            IpNetwork.parse(
                "10.20.0.0/16"
            );

        assertThat(
            network.contains(
                IpAddress.parse(
                    "10.20.255.254"
                )
            )
        )
            .isTrue();

        assertThat(
            network.contains(
                IpAddress.parse(
                    "10.21.0.1"
                )
            )
        )
            .isFalse();
    }

    @Test
    void shouldMatchIpv6AddressInsideNetwork() {
        IpNetwork network =
            IpNetwork.parse(
                "2001:db8:abcd::/48"
            );

        assertThat(
            network.contains(
                IpAddress.parse(
                    "2001:db8:abcd::42"
                )
            )
        )
            .isTrue();

        assertThat(
            network.contains(
                IpAddress.parse(
                    "2001:db8:abce::1"
                )
            )
        )
            .isFalse();
    }

    @Test
    void shouldNotMatchDifferentAddressFamily() {
        IpNetwork network =
            IpNetwork.parse(
                "10.0.0.0/8"
            );

        assertThat(
            network.contains(
                IpAddress.parse(
                    "2001:db8::1"
                )
            )
        )
            .isFalse();
    }

    @Test
    void shouldRejectNetworkWithHostBits() {
        assertThatThrownBy(
            () -> IpNetwork.parse(
                "10.20.1.1/16"
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessageContaining(
                "host bits"
            );

        assertThatThrownBy(
            () -> IpNetwork.parse(
                "2001:db8::1/64"
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessageContaining(
                "host bits"
            );
    }

    @Test
    void shouldRejectInvalidPrefix() {
        assertThatThrownBy(
            () -> IpNetwork.parse(
                "192.0.2.0/33"
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            );

        assertThatThrownBy(
            () -> IpNetwork.parse(
                "2001:db8::/129"
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            );
    }

    @Test
    void shouldCompareCanonicalNetworksByValue() {
        IpNetwork first =
            IpNetwork.parse(
                "2001:db8::/32"
            );

        IpNetwork second =
            IpNetwork.parse(
                "2001:0db8:0:0:0:0:0:0/32"
            );

        assertThat(first)
            .isEqualTo(second);

        assertThat(first.hashCode())
            .isEqualTo(second.hashCode());
    }
}

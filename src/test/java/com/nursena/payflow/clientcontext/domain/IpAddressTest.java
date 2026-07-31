package com.nursena.payflow.clientcontext.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IpAddressTest {

    @Test
    void shouldParseAndNormalizeIpv4Literal() {
        IpAddress address =
            IpAddress.parse(
                "192.0.2.15"
            );

        assertThat(address.value())
            .isEqualTo("192.0.2.15");

        assertThat(address.isIpv4())
            .isTrue();

        assertThat(address.bitLength())
            .isEqualTo(32);
    }

    @Test
    void shouldRejectHostnameAndAmbiguousIpv4Literal() {
        assertThatThrownBy(
            () -> IpAddress.parse(
                "proxy.internal"
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            );

        assertThatThrownBy(
            () -> IpAddress.parse(
                "192.168.001.10"
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            );
    }

    @Test
    void shouldNormalizeEquivalentIpv6Literals() {
        IpAddress compressed =
            IpAddress.parse(
                "2001:db8::1"
            );

        IpAddress expanded =
            IpAddress.parse(
                "2001:0db8:0:0:0:0:0:1"
            );

        assertThat(compressed)
            .isEqualTo(expanded);

        assertThat(compressed.value())
            .isEqualTo("2001:db8::1");

        assertThat(compressed.isIpv6())
            .isTrue();
    }

    @Test
    void shouldRejectScopedAndMappedIpv6Literals() {
        assertThatThrownBy(
            () -> IpAddress.parse(
                "fe80::1%eth0"
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            );

        assertThatThrownBy(
            () -> IpAddress.parse(
                "::ffff:192.0.2.1"
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            );
    }

    @Test
    void shouldReturnDefensiveByteCopy() {
        IpAddress address =
            IpAddress.parse(
                "203.0.113.7"
            );

        byte[] bytes =
            address.bytes();

        bytes[0] = 0;

        assertThat(address.value())
            .isEqualTo("203.0.113.7");
    }
}

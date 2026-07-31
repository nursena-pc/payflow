package com.nursena.payflow.clientcontext.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.nursena.payflow.clientcontext.domain.IpAddress;

import org.junit.jupiter.api.Test;

class ForwardingHeaderParserTest {

    private final ForwardingHeaderParser parser =
        new ForwardingHeaderParser();

    @Test
    void shouldParseForwardedIpv4AndIgnoreOtherParameters() {
        List<IpAddress> addresses =
            parser.parseForwarded(
                "for=203.0.113.9;proto=https;by=10.0.0.1",
                4
            );

        assertThat(addresses)
            .extracting(IpAddress::value)
            .containsExactly(
                "203.0.113.9"
            );
    }

    @Test
    void shouldParseQuotedIpv4WithPort() {
        List<IpAddress> addresses =
            parser.parseForwarded(
                "for=\"203.0.113.9:8443\"",
                4
            );

        assertThat(addresses)
            .extracting(IpAddress::value)
            .containsExactly(
                "203.0.113.9"
            );
    }

    @Test
    void shouldParseBracketedIpv6WithPort() {
        List<IpAddress> addresses =
            parser.parseForwarded(
                "for=\"[2001:db8::5]:443\"",
                4
            );

        assertThat(addresses)
            .extracting(IpAddress::value)
            .containsExactly(
                "2001:db8::5"
            );
    }

    @Test
    void shouldPreserveForwardingChainOrder() {
        List<IpAddress> addresses =
            parser.parseForwarded(
                "for=198.51.100.7, for=203.0.113.9, for=10.0.0.2",
                4
            );

        assertThat(addresses)
            .extracting(IpAddress::value)
            .containsExactly(
                "198.51.100.7",
                "203.0.113.9",
                "10.0.0.2"
            );
    }

    @Test
    void shouldParseXForwardedForChain() {
        List<IpAddress> addresses =
            parser.parseXForwardedFor(
                "198.51.100.7, 203.0.113.9, 10.0.0.2",
                4
            );

        assertThat(addresses)
            .extracting(IpAddress::value)
            .containsExactly(
                "198.51.100.7",
                "203.0.113.9",
                "10.0.0.2"
            );
    }

    @Test
    void shouldRejectUnknownAndObfuscatedNodes() {
        assertThatThrownBy(
            () -> parser.parseForwarded(
                "for=unknown",
                4
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            );

        assertThatThrownBy(
            () -> parser.parseForwarded(
                "for=_hidden",
                4
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            );
    }

    @Test
    void shouldRejectHostname() {
        assertThatThrownBy(
            () -> parser.parseForwarded(
                "for=proxy.internal",
                4
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            );
    }

    @Test
    void shouldRejectDuplicateForParameter() {
        assertThatThrownBy(
            () -> parser.parseForwarded(
                "for=203.0.113.9;for=198.51.100.7",
                4
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
    void shouldRejectMalformedQuotedValue() {
        assertThatThrownBy(
            () -> parser.parseForwarded(
                "for=\"203.0.113.9",
                4
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            );
    }

    @Test
    void shouldRejectInvalidPort() {
        assertThatThrownBy(
            () -> parser.parseForwarded(
                "for=\"203.0.113.9:70000\"",
                4
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            );
    }

    @Test
    void shouldRejectExcessiveHopCount() {
        assertThatThrownBy(
            () -> parser.parseXForwardedFor(
                "198.51.100.1,198.51.100.2,198.51.100.3",
                2
            )
        )
            .isInstanceOf(
                ForwardingHeaderParser
                    .ExcessiveForwardedHopsException
                    .class
            );
    }
}

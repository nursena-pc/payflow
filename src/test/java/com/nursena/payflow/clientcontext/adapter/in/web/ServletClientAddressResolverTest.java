package com.nursena.payflow.clientcontext.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.nursena.payflow.clientcontext.domain.ClientAddressResolutionOutcome;
import com.nursena.payflow.clientcontext.domain.ClientAddressSource;
import com.nursena.payflow.clientcontext.domain.ResolvedClientAddress;

import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletRequest;

class ServletClientAddressResolverTest {

    private final ServletClientAddressResolver resolver =
        resolver(
            256,
            4
        );

    @Test
    void shouldIgnoreSpoofedHeadersFromUntrustedPeer() {
        MockHttpServletRequest request =
            request(
                "203.0.113.20"
            );

        request.addHeader(
            "Forwarded",
            "for=198.51.100.7"
        );

        request.addHeader(
            "X-Forwarded-For",
            "192.0.2.5"
        );

        ResolvedClientAddress resolved =
            resolver.resolve(request);

        assertThat(resolved.address().value())
            .isEqualTo("203.0.113.20");

        assertThat(resolved.source())
            .isEqualTo(
                ClientAddressSource.DIRECT_PEER
            );

        assertThat(resolved.outcome())
            .isEqualTo(
                ClientAddressResolutionOutcome
                    .UNTRUSTED_PEER
            );
    }

    @Test
    void shouldUseDirectPeerWhenHeaderIsMissing() {
        ResolvedClientAddress resolved =
            resolver.resolve(
                request(
                    "10.0.0.2"
                )
            );

        assertThat(resolved.address().value())
            .isEqualTo("10.0.0.2");

        assertThat(resolved.outcome())
            .isEqualTo(
                ClientAddressResolutionOutcome
                    .MISSING_HEADER
            );
    }

    @Test
    void shouldGiveForwardedHeaderPrecedence() {
        MockHttpServletRequest request =
            request(
                "10.0.0.2"
            );

        request.addHeader(
            "Forwarded",
            "for=203.0.113.9"
        );

        request.addHeader(
            "X-Forwarded-For",
            "198.51.100.7"
        );

        ResolvedClientAddress resolved =
            resolver.resolve(request);

        assertThat(resolved.address().value())
            .isEqualTo("203.0.113.9");

        assertThat(resolved.source())
            .isEqualTo(
                ClientAddressSource.FORWARDED
            );
    }

    @Test
    void shouldResolveSingleTrustedProxy() {
        MockHttpServletRequest request =
            request(
                "10.0.0.2"
            );

        request.addHeader(
            "Forwarded",
            "for=203.0.113.9"
        );

        ResolvedClientAddress resolved =
            resolver.resolve(request);

        assertThat(resolved.address().value())
            .isEqualTo("203.0.113.9");

        assertThat(resolved.usedForwardingHeader())
            .isTrue();
    }

    @Test
    void shouldResolveFirstUntrustedHopFromRight() {
        MockHttpServletRequest request =
            request(
                "10.0.0.3"
            );

        request.addHeader(
            "Forwarded",
            "for=198.51.100.7, for=203.0.113.9, for=10.0.0.2"
        );

        ResolvedClientAddress resolved =
            resolver.resolve(request);

        assertThat(resolved.address().value())
            .isEqualTo("203.0.113.9");
    }

    @Test
    void shouldUseXForwardedForWhenForwardedIsAbsent() {
        MockHttpServletRequest request =
            request(
                "10.0.0.2"
            );

        request.addHeader(
            "X-Forwarded-For",
            "198.51.100.7"
        );

        ResolvedClientAddress resolved =
            resolver.resolve(request);

        assertThat(resolved.address().value())
            .isEqualTo("198.51.100.7");

        assertThat(resolved.source())
            .isEqualTo(
                ClientAddressSource
                    .X_FORWARDED_FOR
            );
    }

    @Test
    void shouldNotDowngradeAfterMalformedForwardedHeader() {
        MockHttpServletRequest request =
            request(
                "10.0.0.2"
            );

        request.addHeader(
            "Forwarded",
            "for=unknown"
        );

        request.addHeader(
            "X-Forwarded-For",
            "198.51.100.7"
        );

        ResolvedClientAddress resolved =
            resolver.resolve(request);

        assertThat(resolved.address().value())
            .isEqualTo("10.0.0.2");

        assertThat(resolved.source())
            .isEqualTo(
                ClientAddressSource.FORWARDED
            );

        assertThat(resolved.outcome())
            .isEqualTo(
                ClientAddressResolutionOutcome
                    .MALFORMED_HEADER
            );
    }

    @Test
    void shouldFallBackForOversizedHeader() {
        MockHttpServletRequest request =
            request(
                "10.0.0.2"
            );

        request.addHeader(
            "Forwarded",
            "for="
                + "1".repeat(253)
        );

        ResolvedClientAddress resolved =
            resolver.resolve(request);

        assertThat(resolved.address().value())
            .isEqualTo("10.0.0.2");

        assertThat(resolved.outcome())
            .isEqualTo(
                ClientAddressResolutionOutcome
                    .OVERSIZED_HEADER
            );
    }

    @Test
    void shouldFallBackForExcessiveHopCount() {
        MockHttpServletRequest request =
            request(
                "10.0.0.2"
            );

        request.addHeader(
            "X-Forwarded-For",
            "198.51.100.1,198.51.100.2,198.51.100.3,198.51.100.4,198.51.100.5"
        );

        ResolvedClientAddress resolved =
            resolver.resolve(request);

        assertThat(resolved.address().value())
            .isEqualTo("10.0.0.2");

        assertThat(resolved.outcome())
            .isEqualTo(
                ClientAddressResolutionOutcome
                    .EXCESSIVE_HOPS
            );
    }

    @Test
    void shouldCombineMultipleForwardedHeaderLines() {
        MockHttpServletRequest request =
            request(
                "10.0.0.3"
            );

        request.addHeader(
            "Forwarded",
            "for=203.0.113.9"
        );

        request.addHeader(
            "Forwarded",
            "for=10.0.0.2"
        );

        ResolvedClientAddress resolved =
            resolver.resolve(request);

        assertThat(resolved.address().value())
            .isEqualTo("203.0.113.9");
    }

    @Test
    void shouldResolveIpv6ClientBehindTrustedIpv6Proxy() {
        MockHttpServletRequest request =
            request(
                "2001:db8:ffff::2"
            );

        request.addHeader(
            "Forwarded",
            "for=\"[2001:db8:abcd::5]:443\""
        );

        ResolvedClientAddress resolved =
            resolver.resolve(request);

        assertThat(resolved.address().value())
            .isEqualTo(
                "2001:db8:abcd::5"
            );
    }

    @Test
    void shouldRejectMalformedDirectPeer() {
        assertThatThrownBy(
            () -> resolver.resolve(
                request(
                    "proxy.internal"
                )
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            );
    }

    private static ServletClientAddressResolver resolver(
        int maximumHeaderLength,
        int maximumHops
    ) {
        return new ServletClientAddressResolver(
            new TrustedProxyProperties(
                List.of(
                    "10.0.0.0/8",
                    "2001:db8:ffff::/48"
                ),
                maximumHeaderLength,
                maximumHops
            )
        );
    }

    private static MockHttpServletRequest request(
        String remoteAddress
    ) {
        MockHttpServletRequest request =
            new MockHttpServletRequest();

        request.setRemoteAddr(remoteAddress);

        return request;
    }
}

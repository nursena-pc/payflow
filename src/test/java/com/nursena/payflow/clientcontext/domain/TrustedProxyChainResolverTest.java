package com.nursena.payflow.clientcontext.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class TrustedProxyChainResolverTest {

    private final TrustedProxyChainResolver resolver =
        new TrustedProxyChainResolver(
            List.of(
                IpNetwork.parse(
                    "10.0.0.0/8"
                ),
                IpNetwork.parse(
                    "2001:db8:ffff::/48"
                )
            )
        );

    @Test
    void shouldKeepUntrustedDirectPeer() {
        IpAddress directPeer =
            IpAddress.parse(
                "203.0.113.9"
            );

        IpAddress resolved =
            resolver.resolve(
                directPeer,
                List.of(
                    IpAddress.parse(
                        "198.51.100.5"
                    )
                )
            );

        assertThat(resolved)
            .isEqualTo(directPeer);
    }

    @Test
    void shouldResolveClientBehindSingleTrustedProxy() {
        IpAddress resolved =
            resolver.resolve(
                IpAddress.parse(
                    "10.0.0.2"
                ),
                List.of(
                    IpAddress.parse(
                        "203.0.113.9"
                    )
                )
            );

        assertThat(resolved.value())
            .isEqualTo("203.0.113.9");
    }

    @Test
    void shouldSelectFirstUntrustedAddressFromRight() {
        IpAddress resolved =
            resolver.resolve(
                IpAddress.parse(
                    "10.0.0.3"
                ),
                List.of(
                    IpAddress.parse(
                        "198.51.100.7"
                    ),
                    IpAddress.parse(
                        "203.0.113.9"
                    ),
                    IpAddress.parse(
                        "10.0.0.2"
                    )
                )
            );

        assertThat(resolved.value())
            .isEqualTo("203.0.113.9");
    }

    @Test
    void shouldReturnLeftmostAddressWhenEveryHopIsTrusted() {
        IpAddress resolved =
            resolver.resolve(
                IpAddress.parse(
                    "10.0.0.3"
                ),
                List.of(
                    IpAddress.parse(
                        "10.0.0.1"
                    ),
                    IpAddress.parse(
                        "10.0.0.2"
                    )
                )
            );

        assertThat(resolved.value())
            .isEqualTo("10.0.0.1");
    }

    @Test
    void shouldResolveMixedIpv4AndIpv6Chain() {
        IpAddress resolved =
            resolver.resolve(
                IpAddress.parse(
                    "2001:db8:ffff::2"
                ),
                List.of(
                    IpAddress.parse(
                        "198.51.100.8"
                    ),
                    IpAddress.parse(
                        "2001:db8:ffff::1"
                    )
                )
            );

        assertThat(resolved.value())
            .isEqualTo("198.51.100.8");
    }
}

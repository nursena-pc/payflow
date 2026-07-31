package com.nursena.payflow.clientcontext.adapter.in.web;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.nursena.payflow.clientcontext.domain.IpNetwork;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(
    prefix = "payflow.security.client-context"
)
public record TrustedProxyProperties(
    List<String> trustedProxyCidrs,
    int maxForwardedHeaderLength,
    int maxForwardedHops
) {

    private static final int MAXIMUM_TRUSTED_NETWORKS =
        64;

    private static final int MINIMUM_HEADER_LENGTH =
        256;

    private static final int MAXIMUM_HEADER_LENGTH =
        16_384;

    private static final int MINIMUM_FORWARD_HOPS =
        1;

    private static final int MAXIMUM_FORWARD_HOPS =
        64;

    public TrustedProxyProperties {
        List<String> configuredCidrs =
            trustedProxyCidrs == null
                ? List.of()
                : trustedProxyCidrs;

        List<String> normalizedCidrs =
            new ArrayList<>();

        Set<String> uniqueCidrs =
            new HashSet<>();

        for (String configuredCidr : configuredCidrs) {
            Objects.requireNonNull(
                configuredCidr,
                "trusted proxy CIDR must not be null"
            );

            String trimmed =
                configuredCidr.trim();

            if (trimmed.isEmpty()) {
                continue;
            }

            IpNetwork network =
                IpNetwork.parse(trimmed);

            if (network.prefixLength() == 0) {
                throw new IllegalArgumentException(
                    "trusted proxy CIDR must not trust all addresses: "
                        + trimmed
                );
            }

            if (!uniqueCidrs.add(network.value())) {
                throw new IllegalArgumentException(
                    "duplicate trusted proxy CIDR: "
                        + network.value()
                );
            }

            normalizedCidrs.add(
                network.value()
            );
        }

        if (
            normalizedCidrs.size()
                > MAXIMUM_TRUSTED_NETWORKS
        ) {
            throw new IllegalArgumentException(
                "trusted proxy CIDR count must not exceed "
                    + MAXIMUM_TRUSTED_NETWORKS
            );
        }

        requireRange(
            maxForwardedHeaderLength,
            MINIMUM_HEADER_LENGTH,
            MAXIMUM_HEADER_LENGTH,
            "maxForwardedHeaderLength"
        );

        requireRange(
            maxForwardedHops,
            MINIMUM_FORWARD_HOPS,
            MAXIMUM_FORWARD_HOPS,
            "maxForwardedHops"
        );

        trustedProxyCidrs =
            List.copyOf(normalizedCidrs);
    }

    public List<IpNetwork> trustedProxyNetworks() {
        return trustedProxyCidrs
            .stream()
            .map(IpNetwork::parse)
            .toList();
    }

    private static void requireRange(
        int value,
        int minimum,
        int maximum,
        String propertyName
    ) {
        if (
            value < minimum
                || value > maximum
        ) {
            throw new IllegalArgumentException(
                propertyName
                    + " must be between "
                    + minimum
                    + " and "
                    + maximum
            );
        }
    }
}

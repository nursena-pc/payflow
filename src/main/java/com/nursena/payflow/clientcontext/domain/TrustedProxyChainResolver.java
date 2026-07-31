package com.nursena.payflow.clientcontext.domain;

import java.util.List;
import java.util.Objects;

public final class TrustedProxyChainResolver {

    private final List<IpNetwork> trustedNetworks;

    public TrustedProxyChainResolver(
        List<IpNetwork> trustedNetworks
    ) {
        Objects.requireNonNull(
            trustedNetworks,
            "trusted networks must not be null"
        );

        this.trustedNetworks =
            List.copyOf(trustedNetworks);
    }

    public boolean isTrusted(IpAddress address) {
        Objects.requireNonNull(
            address,
            "IP address must not be null"
        );

        return trustedNetworks
            .stream()
            .anyMatch(
                network ->
                    network.contains(address)
            );
    }

    public IpAddress resolve(
        IpAddress directPeer,
        List<IpAddress> forwardingChain
    ) {
        Objects.requireNonNull(
            directPeer,
            "direct peer must not be null"
        );

        Objects.requireNonNull(
            forwardingChain,
            "forwarding chain must not be null"
        );

        if (
            !isTrusted(directPeer)
                || forwardingChain.isEmpty()
        ) {
            return directPeer;
        }

        for (
            int index =
                forwardingChain.size() - 1;
            index >= 0;
            index--
        ) {
            IpAddress candidate =
                Objects.requireNonNull(
                    forwardingChain.get(index),
                    "forwarding chain address must not be null"
                );

            if (!isTrusted(candidate)) {
                return candidate;
            }
        }

        return forwardingChain.getFirst();
    }
}

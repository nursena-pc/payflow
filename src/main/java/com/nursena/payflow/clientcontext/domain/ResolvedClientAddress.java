package com.nursena.payflow.clientcontext.domain;

import java.util.Objects;

public record ResolvedClientAddress(
    IpAddress address,
    ClientAddressSource source,
    ClientAddressResolutionOutcome outcome
) {

    public ResolvedClientAddress {
        Objects.requireNonNull(
            address,
            "resolved client address must not be null"
        );

        Objects.requireNonNull(
            source,
            "client address source must not be null"
        );

        Objects.requireNonNull(
            outcome,
            "client address outcome must not be null"
        );
    }

    public boolean usedForwardingHeader() {
        return outcome
            == ClientAddressResolutionOutcome.RESOLVED;
    }
}

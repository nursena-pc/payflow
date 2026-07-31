package com.nursena.payflow.clientcontext.adapter.in.web;

import com.nursena.payflow.clientcontext.domain.ClientAddressResolutionOutcome;
import com.nursena.payflow.clientcontext.domain.ClientAddressSource;

@FunctionalInterface
interface ClientAddressResolutionObserver {

    void record(
        ClientAddressSource source,
        ClientAddressResolutionOutcome outcome
    );

    static ClientAddressResolutionObserver noOp() {
        return (
            source,
            outcome
        ) -> {
        };
    }
}

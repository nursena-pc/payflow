package com.nursena.payflow.clientcontext.domain;

public enum ClientAddressResolutionOutcome {
    DIRECT,
    RESOLVED,
    UNTRUSTED_PEER,
    MISSING_HEADER,
    MALFORMED_HEADER,
    OVERSIZED_HEADER,
    EXCESSIVE_HOPS
}

package com.nursena.payflow.maildelivery.application.port.in;

public record DispatchMailOutboxResult(
    int claimedCount,
    int sentCount,
    int retriedCount,
    int failedCount,
    int unresolvedCount
) {

    public DispatchMailOutboxResult {
        if (claimedCount < 0 || sentCount < 0 || retriedCount < 0
            || failedCount < 0 || unresolvedCount < 0) {
            throw new IllegalArgumentException("result counts must not be negative");
        }
        if (sentCount + retriedCount + failedCount + unresolvedCount != claimedCount) {
            throw new IllegalArgumentException("outcome counts must equal claimedCount");
        }
    }
}

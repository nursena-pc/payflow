package com.nursena.payflow.maildelivery.application.port.in;

import java.time.Duration;
import java.util.Objects;

public record DispatchMailOutboxCommand(
    String workerId,
    int batchSize,
    Duration leaseDuration
) {

    public DispatchMailOutboxCommand {
        if (workerId == null || workerId.isBlank()) {
            throw new IllegalArgumentException("workerId must not be blank");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        Objects.requireNonNull(leaseDuration, "leaseDuration must not be null");
        if (leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
    }
}

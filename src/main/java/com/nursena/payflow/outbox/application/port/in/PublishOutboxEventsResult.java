package com.nursena.payflow.outbox.application.port.in;

public record PublishOutboxEventsResult(
    int claimedCount,
    int publishedCount,
    int retriedCount,
    int failedCount,
    int unresolvedCount
) {

    public PublishOutboxEventsResult {
        ensureNonNegative(
            claimedCount,
            "claimedCount"
        );

        ensureNonNegative(
            publishedCount,
            "publishedCount"
        );

        ensureNonNegative(
            retriedCount,
            "retriedCount"
        );

        ensureNonNegative(
            failedCount,
            "failedCount"
        );

        ensureNonNegative(
            unresolvedCount,
            "unresolvedCount"
        );

        long outcomeCount =
            (long) publishedCount
                + retriedCount
                + failedCount
                + unresolvedCount;

        if (outcomeCount != claimedCount) {
            throw new IllegalArgumentException(
                "Outcome counts must equal "
                    + "claimedCount."
            );
        }
    }

    public static PublishOutboxEventsResult empty() {
        return new PublishOutboxEventsResult(
            0,
            0,
            0,
            0,
            0
        );
    }

    private static void ensureNonNegative(
        int value,
        String fieldName
    ) {
        if (value < 0) {
            throw new IllegalArgumentException(
                fieldName
                    + " must not be negative."
            );
        }
    }
}

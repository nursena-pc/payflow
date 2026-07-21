package com.nursena.payflow.eventprocessing.adapter.in.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(
    prefix =
        "payflow.event-processing"
            + ".transfer-completed"
            + ".dead-letter-intake"
)
record TransferCompletedKafkaDeadLetterIntakeProperties(
    boolean enabled,
    String groupId
) {

    private static final int MAX_GROUP_ID_LENGTH =
        255;

    TransferCompletedKafkaDeadLetterIntakeProperties {
        if (groupId == null || groupId.isBlank()) {
            throw new IllegalArgumentException(
                "groupId must not be blank."
            );
        }

        if (groupId.length()
            > MAX_GROUP_ID_LENGTH) {

            throw new IllegalArgumentException(
                "groupId must not exceed "
                    + MAX_GROUP_ID_LENGTH
                    + " characters."
            );
        }
    }
}

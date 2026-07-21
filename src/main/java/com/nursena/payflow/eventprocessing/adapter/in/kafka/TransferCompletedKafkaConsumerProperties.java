package com.nursena.payflow.eventprocessing.adapter.in.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(
    prefix =
        "payflow.event-processing.transfer-completed"
)
record TransferCompletedKafkaConsumerProperties(
    boolean enabled,
    String topic,
    String groupId,
    String consumerName
) {

    private static final int MAX_TOPIC_LENGTH = 200;

    private static final int MAX_CONSUMER_NAME_LENGTH =
        200;

    TransferCompletedKafkaConsumerProperties {
        validateText(
            topic,
            "topic",
            MAX_TOPIC_LENGTH
        );

        validateText(
            groupId,
            "groupId",
            Integer.MAX_VALUE
        );

        validateText(
            consumerName,
            "consumerName",
            MAX_CONSUMER_NAME_LENGTH
        );
    }

    private static void validateText(
        String value,
        String fieldName,
        int maximumLength
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                fieldName + " must not be blank."
            );
        }

        if (value.length() > maximumLength) {
            throw new IllegalArgumentException(
                fieldName
                    + " must not exceed "
                    + maximumLength
                    + " characters."
            );
        }
    }
}

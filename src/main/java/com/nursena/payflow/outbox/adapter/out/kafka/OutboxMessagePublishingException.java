package com.nursena.payflow.outbox.adapter.out.kafka;

import java.util.UUID;

public final class OutboxMessagePublishingException
    extends RuntimeException {

    private final UUID eventId;
    private final String topic;

    OutboxMessagePublishingException(
        UUID eventId,
        String topic,
        String reason,
        Throwable cause
    ) {
        super(
            "Failed to publish outbox event "
                + eventId
                + " to Kafka topic '"
                + topic
                + "'. "
                + reason,
            cause
        );

        this.eventId = eventId;
        this.topic = topic;
    }

    public UUID eventId() {
        return eventId;
    }

    public String topic() {
        return topic;
    }
}

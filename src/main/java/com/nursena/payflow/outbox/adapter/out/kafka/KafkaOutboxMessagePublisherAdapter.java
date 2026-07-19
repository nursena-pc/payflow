package com.nursena.payflow.outbox.adapter.out.kafka;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.nursena.payflow.outbox.application.port.out.OutboxMessagePublisherPort;
import com.nursena.payflow.outbox.domain.model.OutboxEvent;
import org.springframework.kafka.core.KafkaOperations;

public final class KafkaOutboxMessagePublisherAdapter
    implements OutboxMessagePublisherPort {

    private final KafkaOperations<String, String>
        kafkaOperations;

    private final Duration sendTimeout;

    KafkaOutboxMessagePublisherAdapter(
        KafkaOperations<String, String>
            kafkaOperations,
        Duration sendTimeout
    ) {
        this.kafkaOperations =
            Objects.requireNonNull(
                kafkaOperations,
                "kafkaOperations must not be null"
            );

        this.sendTimeout =
            Objects.requireNonNull(
                sendTimeout,
                "sendTimeout must not be null"
            );
    }

    @Override
    public void publish(
        OutboxEvent event
    ) {
        Objects.requireNonNull(
            event,
            "event must not be null"
        );

        try {
            kafkaOperations
                .send(
                    event.topic(),
                    event.partitionKey(),
                    event.payload()
                )
                .get(
                    sendTimeout.toMillis(),
                    TimeUnit.MILLISECONDS
                );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw publishingFailure(
                event,
                "Kafka send was interrupted.",
                exception
            );
        } catch (TimeoutException exception) {
            throw publishingFailure(
                event,
                "Kafka broker acknowledgement "
                    + "timed out.",
                exception
            );
        } catch (ExecutionException exception) {
            Throwable cause =
                exception.getCause() == null
                    ? exception
                    : exception.getCause();

            throw publishingFailure(
                event,
                "Kafka broker rejected or failed "
                    + "the send operation.",
                cause
            );
        } catch (RuntimeException exception) {
            throw publishingFailure(
                event,
                "Kafka send could not be started.",
                exception
            );
        }
    }

    private static OutboxMessagePublishingException
    publishingFailure(
        OutboxEvent event,
        String reason,
        Throwable cause
    ) {
        return new OutboxMessagePublishingException(
            event.id(),
            event.topic(),
            reason,
            cause
        );
    }
}

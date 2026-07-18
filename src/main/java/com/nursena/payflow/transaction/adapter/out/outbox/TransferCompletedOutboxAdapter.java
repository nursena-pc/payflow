package com.nursena.payflow.transaction.adapter.out.outbox;

import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nursena.payflow.outbox.application.port.out.OutboxEventRepositoryPort;
import com.nursena.payflow.outbox.domain.model.OutboxEvent;
import com.nursena.payflow.transaction.application.model.TransferCompletedEvent;
import com.nursena.payflow.transaction.application.port.out.TransferCompletedEventRecorderPort;
import org.springframework.stereotype.Component;

@Component
class TransferCompletedOutboxAdapter
    implements TransferCompletedEventRecorderPort {

    private static final String AGGREGATE_TYPE =
        "PAYMENT_TRANSACTION";

    private static final String TOPIC =
        TransferCompletedEvent.TYPE;

    private final OutboxEventRepositoryPort
        outboxEventRepository;

    private final ObjectMapper objectMapper;

    TransferCompletedOutboxAdapter(
        OutboxEventRepositoryPort outboxEventRepository,
        ObjectMapper objectMapper
    ) {
        this.outboxEventRepository =
            outboxEventRepository;

        this.objectMapper = objectMapper;
    }

    @Override
    public void record(
        TransferCompletedEvent event
    ) {
        Objects.requireNonNull(
            event,
            "event must not be null"
        );

        OutboxEvent outboxEvent =
            OutboxEvent.pending(
                event.eventId(),
                AGGREGATE_TYPE,
                event.transactionId(),
                event.eventType(),
                event.eventVersion(),
                TOPIC,
                event.transactionId().toString(),
                deduplicationKey(event),
                serialize(event),
                event.occurredAt()
            );

        outboxEventRepository.save(
            outboxEvent
        );
    }

    private String serialize(
        TransferCompletedEvent event
    ) {
        try {
            return objectMapper
                .writeValueAsString(event);
        } catch (
            JsonProcessingException exception
        ) {
            throw new
                TransferCompletedEventSerializationException(
                exception
            );
        }
    }

    private static String deduplicationKey(
        TransferCompletedEvent event
    ) {
        return event.eventType()
            + ":"
            + event.eventVersion()
            + ":"
            + event.transactionId();
    }
}

package com.nursena.payflow.eventprocessing.adapter.in.kafka;

import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nursena.payflow.eventprocessing.application.model.ProcessTransferCompletedEventCommand;
import com.nursena.payflow.eventprocessing.application.port.in.ProcessTransferCompletedEventUseCase;
import com.nursena.payflow.transaction.application.model.TransferCompletedEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class TransferCompletedKafkaListener {

    private final ProcessTransferCompletedEventUseCase
        useCase;

    private final ObjectMapper objectMapper;

    TransferCompletedKafkaListener(
        @Qualifier(
            "transferCompletedAuditEventProcessor"
        )
        ProcessTransferCompletedEventUseCase useCase,
        ObjectMapper objectMapper
    ) {
        this.useCase =
            Objects.requireNonNull(
                useCase,
                "useCase must not be null"
            );

        this.objectMapper =
            Objects.requireNonNull(
                objectMapper,
                "objectMapper must not be null"
            );
    }

    @KafkaListener(
        id = "transferCompletedAuditKafkaListener",
        idIsGroup = false,
        topics =
            "${payflow.event-processing.transfer-completed.topic}",
        groupId =
            "${payflow.event-processing.transfer-completed.group-id}",
        containerFactory =
            "transferCompletedKafkaListenerContainerFactory",
        autoStartup =
            "${payflow.event-processing.transfer-completed.enabled}"

    )
    void consume(
        ConsumerRecord<String, String> record
    ) {
        Objects.requireNonNull(
            record,
            "record must not be null"
        );

        TransferCompletedEvent event =
            deserialize(record);

        validatePartitionKey(
            record.key(),
            event
        );

        useCase.process(
            new ProcessTransferCompletedEventCommand(
                event,
                record.topic(),
                record.partition(),
                record.offset()
            )
        );
    }

    private TransferCompletedEvent deserialize(
        ConsumerRecord<String, String> record
    ) {
        if (record.value() == null
            || record.value().isBlank()) {

            throw new
                InvalidTransferCompletedKafkaRecordException(
                "Kafka record value must not be blank."
            );
        }

        try {
            return objectMapper.readValue(
                record.value(),
                TransferCompletedEvent.class
            );
        } catch (
            JsonProcessingException exception
        ) {
            throw new
                TransferCompletedEventDeserializationException(
                record.topic(),
                record.partition(),
                record.offset(),
                exception
            );
        }
    }

    private static void validatePartitionKey(
        String partitionKey,
        TransferCompletedEvent event
    ) {
        String expectedKey =
            event.transactionId().toString();

        if (!expectedKey.equals(partitionKey)) {
            throw new
                InvalidTransferCompletedKafkaRecordException(
                "Kafka record key must equal "
                    + "transactionId."
            );
        }
    }
}

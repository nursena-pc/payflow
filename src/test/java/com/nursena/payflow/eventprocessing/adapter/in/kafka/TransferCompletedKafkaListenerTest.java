package com.nursena.payflow.eventprocessing.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.nursena.payflow.eventprocessing.application.model.ProcessTransferCompletedEventCommand;
import com.nursena.payflow.eventprocessing.application.port.in.ProcessTransferCompletedEventUseCase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferCompletedKafkaListenerTest {

    private static final String TOPIC =
        "wallet.transfer.completed";

    private static final UUID TRANSACTION_ID =
        UUID.fromString(
            "60000000-0000-0000-0000-000000001101"
        );

    private static final String PAYLOAD = """
        {
          "eventId": "50000000-0000-0000-0000-000000001101",
          "eventType": "wallet.transfer.completed",
          "eventVersion": 1,
          "occurredAt": "2026-07-20T20:00:00Z",
          "transactionId": "60000000-0000-0000-0000-000000001101",
          "sourceWalletId": "70000000-0000-0000-0000-000000001101",
          "targetWalletId": "70000000-0000-0000-0000-000000001102",
          "amount": "125.50",
          "currency": "TRY"
        }
        """;

    @Mock
    private ProcessTransferCompletedEventUseCase
        useCase;

    private TransferCompletedKafkaListener listener;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper =
            JsonMapper.builder()
                .findAndAddModules()
                .build();

        listener =
            new TransferCompletedKafkaListener(
                useCase,
                objectMapper
            );
    }

    @Test
    void shouldConvertKafkaRecordToProcessingCommand() {
        listener.consume(
            record(
                TRANSACTION_ID.toString(),
                PAYLOAD
            )
        );

        ArgumentCaptor<ProcessTransferCompletedEventCommand>
            captor =
            ArgumentCaptor.forClass(
                ProcessTransferCompletedEventCommand
                    .class
            );

        verify(useCase)
            .process(captor.capture());

        ProcessTransferCompletedEventCommand command =
            captor.getValue();

        assertThat(command.event().transactionId())
            .isEqualTo(TRANSACTION_ID);

        assertThat(command.event().occurredAt())
            .isEqualTo(
                Instant.parse(
                    "2026-07-20T20:00:00Z"
                )
            );

        assertThat(command.topic())
            .isEqualTo(TOPIC);

        assertThat(command.partitionNumber())
            .isZero();

        assertThat(command.recordOffset())
            .isEqualTo(25L);
    }

    @Test
    void shouldRejectMalformedJson() {
        assertThatThrownBy(
            () -> listener.consume(
                record(
                    TRANSACTION_ID.toString(),
                    "{invalid-json"
                )
            )
        )
            .isInstanceOf(
                TransferCompletedEventDeserializationException
                    .class
            )
            .hasMessageContaining(
                "partition 0, offset 25"
            );

        verify(
            useCase,
            never()
        )
            .process(
                org.mockito.ArgumentMatchers.any()
            );
    }

    @Test
    void shouldRejectPartitionKeyMismatch() {
        assertThatThrownBy(
            () -> listener.consume(
                record(
                    UUID.randomUUID().toString(),
                    PAYLOAD
                )
            )
        )
            .isInstanceOf(
                InvalidTransferCompletedKafkaRecordException
                    .class
            )
            .hasMessage(
                "Kafka record key must equal "
                    + "transactionId."
            );

        verify(
            useCase,
            never()
        )
            .process(
                org.mockito.ArgumentMatchers.any()
            );
    }

    @Test
    void shouldRejectBlankPayload() {
        assertThatThrownBy(
            () -> listener.consume(
                record(
                    TRANSACTION_ID.toString(),
                    " "
                )
            )
        )
            .isInstanceOf(
                InvalidTransferCompletedKafkaRecordException
                    .class
            )
            .hasMessage(
                "Kafka record value must not be blank."
            );

        verify(
            useCase,
            never()
        )
            .process(
                org.mockito.ArgumentMatchers.any()
            );
    }

    private static ConsumerRecord<String, String>
    record(
        String key,
        String value
    ) {
        return new ConsumerRecord<>(
            TOPIC,
            0,
            25L,
            key,
            value
        );
    }
}

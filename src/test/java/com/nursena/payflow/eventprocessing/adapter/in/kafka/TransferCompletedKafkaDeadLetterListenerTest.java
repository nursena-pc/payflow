package com.nursena.payflow.eventprocessing.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import com.nursena.payflow.eventprocessing.application.model.RecordKafkaDeadLetterCommand;
import com.nursena.payflow.eventprocessing.application.port.in.RecordKafkaDeadLetterUseCase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.KafkaHeaders;

@ExtendWith(MockitoExtension.class)
class TransferCompletedKafkaDeadLetterListenerTest {

    private static final String DEAD_LETTER_TOPIC =
        "wallet.transfer.completed.dlt";

    private static final String ORIGINAL_TOPIC =
        "wallet.transfer.completed";

    private static final String ORIGINAL_GROUP =
        "payflow-transfer-completed-audit-v1";

    @Mock
    private RecordKafkaDeadLetterUseCase useCase;

    private TransferCompletedKafkaDeadLetterListener
        listener;

    @BeforeEach
    void setUp() {
        listener =
            new TransferCompletedKafkaDeadLetterListener(
                useCase
            );
    }

    @Test
    void shouldConvertDeadLetterRecordToCommand() {
        ConsumerRecord<String, String> record =
            validRecord(
                "transaction-id",
                "{invalid-json"
            );

        /*
         * Verify that accumulated headers resolve
         * to the latest value.
         */
        record.headers()
            .add(
                new RecordHeader(
                    KafkaHeaders.DLT_ORIGINAL_TOPIC,
                    bytes(ORIGINAL_TOPIC)
                )
            );

        listener.consume(record);

        ArgumentCaptor<RecordKafkaDeadLetterCommand>
            captor =
            ArgumentCaptor.forClass(
                RecordKafkaDeadLetterCommand.class
            );

        verify(useCase)
            .record(captor.capture());

        RecordKafkaDeadLetterCommand command =
            captor.getValue();

        assertThat(command.deadLetterTopic())
            .isEqualTo(DEAD_LETTER_TOPIC);

        assertThat(command.deadLetterPartition())
            .isEqualTo(2);

        assertThat(command.deadLetterOffset())
            .isEqualTo(25L);

        assertThat(command.originalTopic())
            .isEqualTo(ORIGINAL_TOPIC);

        assertThat(command.originalPartition())
            .isEqualTo(1);

        assertThat(command.originalOffset())
            .isEqualTo(42L);

        assertThat(command.originalConsumerGroup())
            .isEqualTo(ORIGINAL_GROUP);

        assertThat(command.recordKey())
            .isEqualTo("transaction-id");

        assertThat(command.payload())
            .isEqualTo("{invalid-json");

        assertThat(command.exceptionType())
            .isEqualTo(
                "java.lang.IllegalStateException"
            );

        assertThat(command.exceptionMessage())
            .isEqualTo(
                "Temporary processing failure."
            );
    }

    @Test
    void shouldPreserveNullableKeyPayloadAndMessage() {
        ConsumerRecord<String, String> record =
            validRecord(
                null,
                null
            );

        record.headers()
            .remove(
                KafkaHeaders.DLT_EXCEPTION_MESSAGE
            );

        listener.consume(record);

        ArgumentCaptor<RecordKafkaDeadLetterCommand>
            captor =
            ArgumentCaptor.forClass(
                RecordKafkaDeadLetterCommand.class
            );

        verify(useCase)
            .record(captor.capture());

        RecordKafkaDeadLetterCommand command =
            captor.getValue();

        assertThat(command.recordKey())
            .isNull();

        assertThat(command.payload())
            .isNull();

        assertThat(command.exceptionMessage())
            .isNull();
    }

    @Test
    void shouldRejectMissingRequiredHeader() {
        ConsumerRecord<String, String> record =
            validRecord(
                "transaction-id",
                "payload"
            );

        record.headers()
            .remove(
                KafkaHeaders.DLT_ORIGINAL_OFFSET
            );

        assertThatThrownBy(
            () -> listener.consume(record)
        )
            .isInstanceOf(
                InvalidKafkaDeadLetterRecordException
                    .class
            )
            .hasMessage(
                "Required Kafka header "
                    + KafkaHeaders.DLT_ORIGINAL_OFFSET
                    + " is missing."
            );

        verify(
            useCase,
            never()
        )
            .record(any());
    }

    @Test
    void shouldRejectMalformedIntegerHeader() {
        ConsumerRecord<String, String> record =
            validRecord(
                "transaction-id",
                "payload"
            );

        record.headers()
            .remove(
                KafkaHeaders.DLT_ORIGINAL_PARTITION
            );

        record.headers()
            .add(
                new RecordHeader(
                    KafkaHeaders
                        .DLT_ORIGINAL_PARTITION,
                    new byte[] {1}
                )
            );

        assertThatThrownBy(
            () -> listener.consume(record)
        )
            .isInstanceOf(
                InvalidKafkaDeadLetterRecordException
                    .class
            )
            .hasMessage(
                "Kafka header "
                    + KafkaHeaders
                    .DLT_ORIGINAL_PARTITION
                    + " must contain "
                    + Integer.BYTES
                    + " bytes."
            );

        verify(
            useCase,
            never()
        )
            .record(any());
    }

    @Test
    void shouldRejectBlankRequiredStringHeader() {
        ConsumerRecord<String, String> record =
            validRecord(
                "transaction-id",
                "payload"
            );

        record.headers()
            .remove(
                KafkaHeaders
                    .DLT_ORIGINAL_CONSUMER_GROUP
            );

        record.headers()
            .add(
                new RecordHeader(
                    KafkaHeaders
                        .DLT_ORIGINAL_CONSUMER_GROUP,
                    bytes(" ")
                )
            );

        assertThatThrownBy(
            () -> listener.consume(record)
        )
            .isInstanceOf(
                InvalidKafkaDeadLetterRecordException
                    .class
            )
            .hasMessage(
                "Required Kafka header "
                    + KafkaHeaders
                    .DLT_ORIGINAL_CONSUMER_GROUP
                    + " must not be blank."
            );

        verify(
            useCase,
            never()
        )
            .record(any());
    }

    private static ConsumerRecord<String, String>
    validRecord(
        String key,
        String payload
    ) {
        ConsumerRecord<String, String> record =
            new ConsumerRecord<>(
                DEAD_LETTER_TOPIC,
                2,
                25L,
                key,
                payload
            );

        record.headers()
            .add(
                new RecordHeader(
                    KafkaHeaders.DLT_ORIGINAL_TOPIC,
                    bytes("obsolete-topic")
                )
            );

        record.headers()
            .add(
                new RecordHeader(
                    KafkaHeaders
                        .DLT_ORIGINAL_PARTITION,
                    integerBytes(1)
                )
            );

        record.headers()
            .add(
                new RecordHeader(
                    KafkaHeaders.DLT_ORIGINAL_OFFSET,
                    longBytes(42L)
                )
            );

        record.headers()
            .add(
                new RecordHeader(
                    KafkaHeaders
                        .DLT_ORIGINAL_CONSUMER_GROUP,
                    bytes(ORIGINAL_GROUP)
                )
            );

        record.headers()
            .add(
                new RecordHeader(
                    KafkaHeaders.DLT_EXCEPTION_FQCN,
                    bytes(
                        "java.lang.IllegalStateException"
                    )
                )
            );

        record.headers()
            .add(
                new RecordHeader(
                    KafkaHeaders.DLT_EXCEPTION_MESSAGE,
                    bytes(
                        "Temporary processing failure."
                    )
                )
            );

        return record;
    }

    private static byte[] bytes(
        String value
    ) {
        return value.getBytes(
            StandardCharsets.UTF_8
        );
    }

    private static byte[] integerBytes(
        int value
    ) {
        return ByteBuffer
            .allocate(Integer.BYTES)
            .putInt(value)
            .array();
    }

    private static byte[] longBytes(
        long value
    ) {
        return ByteBuffer
            .allocate(Long.BYTES)
            .putLong(value)
            .array();
    }
}

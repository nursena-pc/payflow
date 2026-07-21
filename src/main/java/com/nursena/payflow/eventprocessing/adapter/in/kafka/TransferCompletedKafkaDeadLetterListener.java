package com.nursena.payflow.eventprocessing.adapter.in.kafka;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.adapter.kafka.KafkaDeadLetterReplayHeaders;
import com.nursena.payflow.eventprocessing.application.model.RecordKafkaDeadLetterCommand;
import com.nursena.payflow.eventprocessing.application.port.in.RecordKafkaDeadLetterUseCase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

@Component
class TransferCompletedKafkaDeadLetterListener {

    private final RecordKafkaDeadLetterUseCase
        useCase;

    TransferCompletedKafkaDeadLetterListener(
        @Qualifier(
            "transferCompletedKafkaDeadLetterRecorder"
        )
        RecordKafkaDeadLetterUseCase useCase
    ) {
        this.useCase =
            Objects.requireNonNull(
                useCase,
                "useCase must not be null"
            );
    }

    @KafkaListener(
        id =
            "transferCompletedKafkaDeadLetterIntakeListener",
        idIsGroup = false,
        topics =
            "${payflow.event-processing"
                + ".transfer-completed.failure"
                + ".dead-letter-topic}",
        groupId =
            "${payflow.event-processing"
                + ".transfer-completed"
                + ".dead-letter-intake.group-id}",
        containerFactory =
            "transferCompletedKafkaDeadLetter"
                + "ListenerContainerFactory",
        autoStartup =
            "${payflow.event-processing"
                + ".transfer-completed"
                + ".dead-letter-intake.enabled}"
    )
    void consume(
        ConsumerRecord<String, String> record
    ) {
        Objects.requireNonNull(
            record,
            "record must not be null"
        );

        validateRecordMetadata(record);

        ReplayMetadata replayMetadata =
            replayMetadata(record);

        useCase.record(
            new RecordKafkaDeadLetterCommand(
                record.topic(),
                record.partition(),
                record.offset(),
                requiredStringHeader(
                    record,
                    KafkaHeaders.DLT_ORIGINAL_TOPIC
                ),
                requiredIntegerHeader(
                    record,
                    KafkaHeaders
                        .DLT_ORIGINAL_PARTITION
                ),
                requiredLongHeader(
                    record,
                    KafkaHeaders.DLT_ORIGINAL_OFFSET
                ),
                requiredStringHeader(
                    record,
                    KafkaHeaders
                        .DLT_ORIGINAL_CONSUMER_GROUP
                ),
                record.key(),
                record.value(),
                requiredStringHeader(
                    record,
                    KafkaHeaders.DLT_EXCEPTION_FQCN
                ),
                optionalStringHeader(
                    record,
                    KafkaHeaders
                        .DLT_EXCEPTION_MESSAGE
                ),
                replayMetadata.originId(),
                replayMetadata.attemptBase()
            )
        );
    }

    private static ReplayMetadata replayMetadata(
        ConsumerRecord<?, ?> record
    ) {
        Header originHeader =
            record.headers()
                .lastHeader(
                    KafkaDeadLetterReplayHeaders
                        .REPLAY_ORIGIN_ID
                );

        Header attemptHeader =
            record.headers()
                .lastHeader(
                    KafkaDeadLetterReplayHeaders
                        .REPLAY_ATTEMPT
                );

        if (
            originHeader == null
                && attemptHeader == null
        ) {
            return new ReplayMetadata(
                null,
                null
            );
        }

        if (
            originHeader == null
                || attemptHeader == null
        ) {
            throw invalid(
                "Replay origin id and attempt "
                    + "headers must either both "
                    + "be present or both be absent."
            );
        }

        String originValue =
            requiredStringHeader(
                record,
                KafkaDeadLetterReplayHeaders
                    .REPLAY_ORIGIN_ID
            );

        String attemptValue =
            requiredStringHeader(
                record,
                KafkaDeadLetterReplayHeaders
                    .REPLAY_ATTEMPT
            );

        UUID originId;

        try {
            originId =
                UUID.fromString(originValue);
        } catch (IllegalArgumentException exception) {
            throw invalid(
                "Kafka replay origin id header "
                    + "must contain a valid UUID."
            );
        }

        int attempt;

        try {
            attempt =
                Integer.parseInt(attemptValue);
        } catch (NumberFormatException exception) {
            throw invalid(
                "Kafka replay attempt header "
                    + "must contain an integer."
            );
        }

        if (attempt <= 0) {
            throw invalid(
                "Kafka replay attempt header "
                    + "must be positive."
            );
        }

        return new ReplayMetadata(
            originId,
            attempt
        );
    }

    private static void validateRecordMetadata(
        ConsumerRecord<?, ?> record
    ) {
        if (
            record.topic() == null
                || record.topic().isBlank()
        ) {
            throw invalid(
                "DLT record topic must not be blank."
            );
        }

        if (record.partition() < 0) {
            throw invalid(
                "DLT record partition "
                    + "must not be negative."
            );
        }

        if (record.offset() < 0) {
            throw invalid(
                "DLT record offset "
                    + "must not be negative."
            );
        }
    }

    private static String requiredStringHeader(
        ConsumerRecord<?, ?> record,
        String headerName
    ) {
        String value =
            new String(
                requiredHeaderValue(
                    record,
                    headerName
                ),
                StandardCharsets.UTF_8
            );

        if (value.isBlank()) {
            throw invalid(
                "Required Kafka header "
                    + headerName
                    + " must not be blank."
            );
        }

        return value;
    }

    private static String optionalStringHeader(
        ConsumerRecord<?, ?> record,
        String headerName
    ) {
        Header header =
            record.headers()
                .lastHeader(headerName);

        if (
            header == null
                || header.value() == null
        ) {
            return null;
        }

        return new String(
            header.value(),
            StandardCharsets.UTF_8
        );
    }

    private static int requiredIntegerHeader(
        ConsumerRecord<?, ?> record,
        String headerName
    ) {
        byte[] value =
            requiredHeaderValue(
                record,
                headerName
            );

        if (
            value.length
                != Integer.BYTES
        ) {
            throw invalid(
                "Kafka header "
                    + headerName
                    + " must contain "
                    + Integer.BYTES
                    + " bytes."
            );
        }

        return ByteBuffer
            .wrap(value)
            .getInt();
    }

    private static long requiredLongHeader(
        ConsumerRecord<?, ?> record,
        String headerName
    ) {
        byte[] value =
            requiredHeaderValue(
                record,
                headerName
            );

        if (
            value.length
                != Long.BYTES
        ) {
            throw invalid(
                "Kafka header "
                    + headerName
                    + " must contain "
                    + Long.BYTES
                    + " bytes."
            );
        }

        return ByteBuffer
            .wrap(value)
            .getLong();
    }

    private static byte[] requiredHeaderValue(
        ConsumerRecord<?, ?> record,
        String headerName
    ) {
        Header header =
            record.headers()
                .lastHeader(headerName);

        if (
            header == null
                || header.value() == null
                || header.value().length == 0
        ) {
            throw invalid(
                "Required Kafka header "
                    + headerName
                    + " is missing."
            );
        }

        return header.value();
    }

    private static
    InvalidKafkaDeadLetterRecordException
    invalid(
        String message
    ) {
        return new
            InvalidKafkaDeadLetterRecordException(
            message
        );
    }

    private record ReplayMetadata(
        UUID originId,
        Integer attemptBase
    ) {
    }
}

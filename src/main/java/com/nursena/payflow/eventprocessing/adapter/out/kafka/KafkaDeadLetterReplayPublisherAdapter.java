package com.nursena.payflow.eventprocessing.adapter.out.kafka;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.nursena.payflow.eventprocessing.adapter.kafka.KafkaDeadLetterReplayHeaders;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterReplayPublisherPort;
import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecord;
import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecordStatus;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaOperations;

public final class
KafkaDeadLetterReplayPublisherAdapter
    implements KafkaDeadLetterReplayPublisherPort {

    private final KafkaOperations<String, String>
        kafkaOperations;

    private final Duration sendTimeout;

    public KafkaDeadLetterReplayPublisherAdapter(
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
            validateSendTimeout(
                sendTimeout
            );
    }

    @Override
    public void publish(
        KafkaDeadLetterRecord record
    ) {
        KafkaDeadLetterRecord validatedRecord =
            validateRecord(record);

        ProducerRecord<String, String>
            producerRecord =
            producerRecord(validatedRecord);

        try {
            kafkaOperations
                .send(producerRecord)
                .get(
                    sendTimeout.toMillis(),
                    TimeUnit.MILLISECONDS
                );
        } catch (InterruptedException exception) {
            Thread.currentThread()
                .interrupt();

            throw publishingFailure(
                validatedRecord,
                "Kafka send was interrupted.",
                exception
            );
        } catch (TimeoutException exception) {
            throw publishingFailure(
                validatedRecord,
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
                validatedRecord,
                "Kafka broker rejected or failed "
                    + "the send operation.",
                cause
            );
        } catch (RuntimeException exception) {
            throw publishingFailure(
                validatedRecord,
                "Kafka send could not be started.",
                exception
            );
        }
    }

    private static ProducerRecord<String, String>
    producerRecord(
        KafkaDeadLetterRecord record
    ) {
        ProducerRecord<String, String>
            producerRecord =
            new ProducerRecord<>(
                record.originalTopic(),
                record.recordKey(),
                record.payload()
            );

        producerRecord.headers()
            .add(
                new RecordHeader(
                    KafkaDeadLetterReplayHeaders
                        .REPLAY_ORIGIN_ID,
                    record.replayOriginId()
                        .toString()
                        .getBytes(UTF_8)
                )
            );

        producerRecord.headers()
            .add(
                new RecordHeader(
                    KafkaDeadLetterReplayHeaders
                        .REPLAY_ATTEMPT,
                    Integer.toString(
                        totalReplayAttempts(
                            record
                        )
                    ).getBytes(UTF_8)
                )
            );

        return producerRecord;
    }

    private static KafkaDeadLetterRecord
    validateRecord(
        KafkaDeadLetterRecord record
    ) {
        KafkaDeadLetterRecord validatedRecord =
            Objects.requireNonNull(
                record,
                "record must not be null"
            );

        if (
            validatedRecord.status()
                != KafkaDeadLetterRecordStatus
                .REPLAYING
        ) {
            throw new IllegalArgumentException(
                "Only REPLAYING dead-letter "
                    + "records may be published."
            );
        }

        if (
            validatedRecord.payload() == null
                || validatedRecord.payload()
                .isBlank()
        ) {
            throw new IllegalArgumentException(
                "Replay payload must not be blank."
            );
        }

        if (
            validatedRecord.originalTopic()
                .equals(
                    validatedRecord
                        .deadLetterTopic()
                )
        ) {
            throw new IllegalArgumentException(
                "Replay source topic must differ "
                    + "from the dead-letter topic."
            );
        }

        totalReplayAttempts(
            validatedRecord
        );

        return validatedRecord;
    }

    private static int totalReplayAttempts(
        KafkaDeadLetterRecord record
    ) {
        int total;

        try {
            total =
                Math.addExact(
                    record.replayAttemptBase(),
                    record.replayCount()
                );
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                "Total replay attempt count "
                    + "must not overflow.",
                exception
            );
        }

        if (total <= 0) {
            throw new IllegalArgumentException(
                "Total replay attempt count "
                    + "must be positive."
            );
        }

        return total;
    }

    private static Duration validateSendTimeout(
        Duration value
    ) {
        Duration validated =
            Objects.requireNonNull(
                value,
                "sendTimeout must not be null"
            );

        if (
            validated.isZero()
                || validated.isNegative()
        ) {
            throw new IllegalArgumentException(
                "sendTimeout must be positive."
            );
        }

        if (validated.toMillis() == 0) {
            throw new IllegalArgumentException(
                "sendTimeout must be at least "
                    + "one millisecond."
            );
        }

        return validated;
    }

    private static
    KafkaDeadLetterReplayPublishingException
    publishingFailure(
        KafkaDeadLetterRecord record,
        String reason,
        Throwable cause
    ) {
        return new
            KafkaDeadLetterReplayPublishingException(
            record.id(),
            record.originalTopic(),
            reason,
            cause
        );
    }
}

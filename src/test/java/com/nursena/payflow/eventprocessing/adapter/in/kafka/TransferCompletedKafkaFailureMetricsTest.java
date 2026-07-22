package com.nursena.payflow.eventprocessing.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class TransferCompletedKafkaFailureMetricsTest {

    private static final String CONSUMER_NAME =
        "transfer-completed-audit";

    private static final String TOPIC =
        "wallet.transfer.completed";

    private SimpleMeterRegistry meterRegistry;

    private TransferCompletedKafkaFailureMetrics
        metrics;

    @BeforeEach
    void setUp() {
        meterRegistry =
            new SimpleMeterRegistry();

        metrics =
            new TransferCompletedKafkaFailureMetrics(
                meterRegistry,
                CONSUMER_NAME,
                List.of(
                    InvalidTransferCompletedKafkaRecordException
                        .class,
                    TransferCompletedEventDeserializationException
                        .class,
                    DataIntegrityViolationException.class
                )
            );
    }

    @AfterEach
    void tearDown() {
        meterRegistry.close();
    }

    @Test
    void shouldRecordPermanentFailureAndSuccessfulRecovery() {
        Exception failure =
            new InvalidTransferCompletedKafkaRecordException(
                "Invalid partition key."
            );

        metrics.failedDelivery(
            record(),
            failure,
            1
        );

        metrics.recovered(
            record(),
            failure
        );

        assertDeliveryFailureCount(
            "permanent",
            "InvalidTransferCompletedKafkaRecordException",
            1.0
        );

        assertThat(
            meterRegistry.find(
                    TransferCompletedKafkaFailureMetrics
                        .RETRY_ATTEMPTS_METRIC
                )
                .counter()
        )
            .isNull();

        assertRecoveryCount(
            "success",
            "permanent",
            "InvalidTransferCompletedKafkaRecordException",
            1.0
        );
    }

    @Test
    void shouldRecordRetryableDeliveriesAndRetryAttempts() {
        Exception failure =
            new IllegalStateException(
                "Database temporarily unavailable."
            );

        metrics.failedDelivery(
            record(),
            failure,
            1
        );

        metrics.failedDelivery(
            record(),
            failure,
            2
        );

        metrics.failedDelivery(
            record(),
            failure,
            3
        );

        metrics.recovered(
            record(),
            failure
        );

        assertDeliveryFailureCount(
            "retryable",
            "IllegalStateException",
            3.0
        );

        assertRetryAttemptCount(
            "retryable",
            "IllegalStateException",
            2.0
        );

        assertRecoveryCount(
            "success",
            "retryable",
            "IllegalStateException",
            1.0
        );
    }

    @Test
    void shouldRecordFailedRecovery() {
        Exception originalFailure =
            new IllegalStateException(
                "Temporary processing failure."
            );

        Exception recoveryFailure =
            new IllegalStateException(
                "DLT publication failure."
            );

        metrics.recoveryFailed(
            record(),
            originalFailure,
            recoveryFailure
        );

        assertRecoveryCount(
            "failure",
            "retryable",
            "IllegalStateException",
            1.0
        );
    }

    @Test
    void shouldClassifyWrappedPermanentFailure() {
        Exception failure =
            new IllegalStateException(
                "Wrapped database constraint failure.",
                new DataIntegrityViolationException(
                    "Duplicate transaction."
                )
            );

        metrics.failedDelivery(
            record(),
            failure,
            1
        );

        assertDeliveryFailureCount(
            "permanent",
            "DataIntegrityViolationException",
            1.0
        );
    }

    private void assertDeliveryFailureCount(
        String failureType,
        String exception,
        double expected
    ) {
        assertThat(
            counter(
                TransferCompletedKafkaFailureMetrics
                    .DELIVERY_FAILURES_METRIC,
                failureType,
                exception
            )
                .count()
        )
            .isEqualTo(expected);
    }

    private void assertRetryAttemptCount(
        String failureType,
        String exception,
        double expected
    ) {
        assertThat(
            counter(
                TransferCompletedKafkaFailureMetrics
                    .RETRY_ATTEMPTS_METRIC,
                failureType,
                exception
            )
                .count()
        )
            .isEqualTo(expected);
    }

    private void assertRecoveryCount(
        String outcome,
        String failureType,
        String exception,
        double expected
    ) {
        assertThat(
            meterRegistry.get(
                    TransferCompletedKafkaFailureMetrics
                        .RECOVERIES_METRIC
                )
                .tags(
                    "consumer",
                    CONSUMER_NAME,
                    "topic",
                    TOPIC,
                    "failure_type",
                    failureType,
                    "exception",
                    exception,
                    "outcome",
                    outcome
                )
                .counter()
                .count()
        )
            .isEqualTo(expected);
    }

    private Counter counter(
        String metricName,
        String failureType,
        String exception
    ) {
        return meterRegistry.get(
                metricName
            )
            .tags(
                "consumer",
                CONSUMER_NAME,
                "topic",
                TOPIC,
                "failure_type",
                failureType,
                "exception",
                exception
            )
            .counter();
    }

    private static ConsumerRecord<String, String>
    record() {
        return new ConsumerRecord<>(
            TOPIC,
            0,
            25L,
            "transaction-id",
            "{}"
        );
    }
}

package com.nursena.payflow.eventprocessing.adapter.in.kafka;

import java.util.List;
import java.util.Objects;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.RetryListener;

final class TransferCompletedKafkaFailureMetrics
    implements RetryListener {

    static final String DELIVERY_FAILURES_METRIC =
        "payflow.kafka.consumer.delivery.failures";

    static final String RETRY_ATTEMPTS_METRIC =
        "payflow.kafka.consumer.retry.attempts";

    static final String RECOVERIES_METRIC =
        "payflow.kafka.consumer.recoveries";

    private static final String CONSUMER_TAG =
        "consumer";

    private static final String TOPIC_TAG =
        "topic";

    private static final String FAILURE_TYPE_TAG =
        "failure_type";

    private static final String EXCEPTION_TAG =
        "exception";

    private static final String OUTCOME_TAG =
        "outcome";

    private static final String PERMANENT_FAILURE =
        "permanent";

    private static final String RETRYABLE_FAILURE =
        "retryable";

    private final MeterRegistry meterRegistry;

    private final String consumerName;

    private final List<Class<? extends Exception>>
        nonRetryableExceptions;

    TransferCompletedKafkaFailureMetrics(
        MeterRegistry meterRegistry,
        String consumerName,
        List<Class<? extends Exception>>
            nonRetryableExceptions
    ) {
        this.meterRegistry =
            Objects.requireNonNull(
                meterRegistry,
                "meterRegistry must not be null"
            );

        this.consumerName =
            validateConsumerName(
                consumerName
            );

        this.nonRetryableExceptions =
            List.copyOf(
                Objects.requireNonNull(
                    nonRetryableExceptions,
                    "nonRetryableExceptions "
                        + "must not be null"
                )
            );
    }

    @Override
    public void failedDelivery(
        ConsumerRecord<?, ?> record,
        Exception exception,
        int deliveryAttempt
    ) {
        validateCallbackArguments(
            record,
            exception
        );

        deliveryFailureCounter(
            record,
            exception
        )
            .increment();

        /*
         * Delivery attempt one is the original
         * delivery. Values above one represent
         * actual retries.
         */
        if (deliveryAttempt > 1) {
            retryAttemptCounter(
                record,
                exception
            )
                .increment();
        }
    }

    @Override
    public void recovered(
        ConsumerRecord<?, ?> record,
        Exception exception
    ) {
        validateCallbackArguments(
            record,
            exception
        );

        recoveryCounter(
            record,
            exception,
            "success"
        )
            .increment();
    }

    @Override
    public void recoveryFailed(
        ConsumerRecord<?, ?> record,
        Exception originalException,
        Exception recoveryException
    ) {
        validateCallbackArguments(
            record,
            originalException
        );

        Objects.requireNonNull(
            recoveryException,
            "recoveryException must not be null"
        );

        recoveryCounter(
            record,
            originalException,
            "failure"
        )
            .increment();
    }

    private Counter deliveryFailureCounter(
        ConsumerRecord<?, ?> record,
        Exception exception
    ) {
        return Counter.builder(
                DELIVERY_FAILURES_METRIC
            )
            .description(
                "Number of failed Kafka consumer "
                    + "delivery attempts."
            )
            .baseUnit("events")
            .tags(
                CONSUMER_TAG,
                consumerName,
                TOPIC_TAG,
                record.topic(),
                FAILURE_TYPE_TAG,
                failureType(exception),
                EXCEPTION_TAG,
                exceptionName(exception)
            )
            .register(
                meterRegistry
            );
    }

    private Counter retryAttemptCounter(
        ConsumerRecord<?, ?> record,
        Exception exception
    ) {
        return Counter.builder(
                RETRY_ATTEMPTS_METRIC
            )
            .description(
                "Number of Kafka consumer retry "
                    + "attempts after initial delivery."
            )
            .baseUnit("attempts")
            .tags(
                CONSUMER_TAG,
                consumerName,
                TOPIC_TAG,
                record.topic(),
                FAILURE_TYPE_TAG,
                failureType(exception),
                EXCEPTION_TAG,
                exceptionName(exception)
            )
            .register(
                meterRegistry
            );
    }

    private Counter recoveryCounter(
        ConsumerRecord<?, ?> record,
        Exception exception,
        String outcome
    ) {
        return Counter.builder(
                RECOVERIES_METRIC
            )
            .description(
                "Number of Kafka consumer "
                    + "dead-letter recovery outcomes."
            )
            .baseUnit("events")
            .tags(
                CONSUMER_TAG,
                consumerName,
                TOPIC_TAG,
                record.topic(),
                FAILURE_TYPE_TAG,
                failureType(exception),
                EXCEPTION_TAG,
                exceptionName(exception),
                OUTCOME_TAG,
                outcome
            )
            .register(
                meterRegistry
            );
    }

    private String failureType(
        Exception exception
    ) {
        return containsNonRetryableCause(
            exception
        )
            ? PERMANENT_FAILURE
            : RETRYABLE_FAILURE;
    }

    private boolean containsNonRetryableCause(
        Throwable exception
    ) {
        Throwable current = exception;

        while (current != null) {
            for (
                Class<? extends Exception> type
                : nonRetryableExceptions
            ) {
                if (type.isInstance(current)) {
                    return true;
                }
            }

            current = current.getCause();
        }

        return false;
    }

    private static String exceptionName(
        Throwable exception
    ) {
        Throwable rootCause =
            rootCauseOf(exception);

        String simpleName =
            rootCause
                .getClass()
                .getSimpleName();

        if (simpleName.isBlank()) {
            return rootCause
                .getClass()
                .getName();
        }

        return simpleName;
    }

    private static Throwable rootCauseOf(
        Throwable exception
    ) {
        Throwable current = exception;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        return current;
    }

    private static void validateCallbackArguments(
        ConsumerRecord<?, ?> record,
        Exception exception
    ) {
        Objects.requireNonNull(
            record,
            "record must not be null"
        );

        Objects.requireNonNull(
            exception,
            "exception must not be null"
        );
    }

    private static String validateConsumerName(
        String consumerName
    ) {
        if (consumerName == null
            || consumerName.isBlank()) {

            throw new IllegalArgumentException(
                "consumerName must not be blank."
            );
        }

        return consumerName;
    }
}

package com.nursena.payflow.eventprocessing.adapter.in.kafka;

import java.util.List;
import java.util.Objects;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(
    TransferCompletedKafkaFailureProperties.class
)
class TransferCompletedKafkaFailureConfiguration {

    private static final List<
        Class<? extends Exception>
        > NON_RETRYABLE_EXCEPTIONS = List.of(
        InvalidTransferCompletedKafkaRecordException
            .class,
        TransferCompletedEventDeserializationException
            .class,
        DataIntegrityViolationException.class
    );

    @Bean(
        "transferCompletedKafkaListenerContainerFactory"
    )
    ConcurrentKafkaListenerContainerFactory<
        Object,
        Object
        > transferCompletedKafkaListenerContainerFactory(
        ConcurrentKafkaListenerContainerFactoryConfigurer
            configurer,
        ConsumerFactory<Object, Object>
            consumerFactory,
        KafkaTemplate<String, String>
            kafkaTemplate,
        TransferCompletedKafkaConsumerProperties
            consumerProperties,
        TransferCompletedKafkaFailureProperties
            failureProperties
    ) {
        validateDistinctTopics(
            consumerProperties.topic(),
            failureProperties.deadLetterTopic()
        );

        ConcurrentKafkaListenerContainerFactory<
            Object,
            Object
            > factory =
            new ConcurrentKafkaListenerContainerFactory<>();

        configurer.configure(
            factory,
            consumerFactory
        );

        factory.setCommonErrorHandler(
            errorHandler(
                kafkaTemplate,
                failureProperties
            )
        );

        return factory;
    }

    static DefaultErrorHandler errorHandler(
        KafkaTemplate<String, String>
            kafkaTemplate,
        TransferCompletedKafkaFailureProperties
            properties
    ) {
        Objects.requireNonNull(
            kafkaTemplate,
            "kafkaTemplate must not be null"
        );

        Objects.requireNonNull(
            properties,
            "properties must not be null"
        );

        DeadLetterPublishingRecoverer recoverer =
            new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) ->
                    new TopicPartition(
                        properties.deadLetterTopic(),
                        record.partition()
                    )
            );

        /*
         * A failed DLT publication must be visible
         * to the error handler. Otherwise the source
         * offset could advance without a durable DLT
         * record.
         */
        recoverer.setFailIfSendResultIsError(
            true
        );

        recoverer.setWaitForSendResultTimeout(
            properties.sendTimeout()
        );

        DefaultErrorHandler errorHandler =
            new DefaultErrorHandler(
                recoverer,
                retryBackOff(properties)
            );

        nonRetryableExceptions()
            .forEach(
                errorHandler
                    ::addNotRetryableExceptions
            );

        return errorHandler;
    }

    static ExponentialBackOffWithMaxRetries
    retryBackOff(
        TransferCompletedKafkaFailureProperties
            properties
    ) {
        Objects.requireNonNull(
            properties,
            "properties must not be null"
        );

        ExponentialBackOffWithMaxRetries backOff =
            new ExponentialBackOffWithMaxRetries(
                properties.maxRetries()
            );

        backOff.setInitialInterval(
            properties.initialDelay().toMillis()
        );

        backOff.setMultiplier(
            properties.multiplier()
        );

        backOff.setMaxInterval(
            properties.maximumDelay().toMillis()
        );

        return backOff;
    }

    static List<Class<? extends Exception>>
    nonRetryableExceptions() {
        return NON_RETRYABLE_EXCEPTIONS;
    }

    static void validateDistinctTopics(
        String sourceTopic,
        String deadLetterTopic
    ) {
        Objects.requireNonNull(
            sourceTopic,
            "sourceTopic must not be null"
        );

        Objects.requireNonNull(
            deadLetterTopic,
            "deadLetterTopic must not be null"
        );

        if (sourceTopic.equals(
            deadLetterTopic
        )) {
            throw new IllegalArgumentException(
                "deadLetterTopic must differ "
                    + "from source topic."
            );
        }
    }
}

package com.nursena.payflow.eventprocessing.adapter.in.kafka;

import java.time.Clock;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.application.port.in.RecordKafkaDeadLetterUseCase;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterRecordRepositoryPort;
import com.nursena.payflow.eventprocessing.application.service.RecordKafkaDeadLetterService;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.CommonContainerStoppingErrorHandler;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(
    TransferCompletedKafkaDeadLetterIntakeProperties
        .class
)
class TransferCompletedKafkaDeadLetterConfiguration {

    @Bean(
        "transferCompletedKafkaDeadLetterRecorder"
    )
    RecordKafkaDeadLetterUseCase
    transferCompletedKafkaDeadLetterRecorder(
        KafkaDeadLetterRecordRepositoryPort repository,
        Clock clock
    ) {
        return new RecordKafkaDeadLetterService(
            repository,
            clock,
            UUID::randomUUID
        );
    }

    @Bean(
        "transferCompletedKafkaDeadLetter"
            + "ListenerContainerFactory"
    )
    ConcurrentKafkaListenerContainerFactory<
        Object,
        Object
        >
    transferCompletedKafkaDeadLetterListenerContainerFactory(
        ConcurrentKafkaListenerContainerFactoryConfigurer
            configurer,
        ConsumerFactory<Object, Object>
            consumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<
            Object,
            Object
            > factory =
            new ConcurrentKafkaListenerContainerFactory<>();

        configurer.configure(
            factory,
            consumerFactory
        );

        CommonContainerStoppingErrorHandler
            errorHandler =
            new CommonContainerStoppingErrorHandler();

        /*
         * Invalid DLT metadata or PostgreSQL
         * persistence failures must not be
         * recovered by skipping the record.
         */
        errorHandler.setStopContainerAbnormally(
            true
        );

        factory.setCommonErrorHandler(
            errorHandler
        );

        return factory;
    }
}

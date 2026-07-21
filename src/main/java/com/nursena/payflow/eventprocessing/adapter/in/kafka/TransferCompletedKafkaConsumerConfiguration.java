package com.nursena.payflow.eventprocessing.adapter.in.kafka;

import java.time.Clock;

import com.nursena.payflow.eventprocessing.application.port.in.ProcessTransferCompletedEventUseCase;
import com.nursena.payflow.eventprocessing.application.port.out.ProcessedKafkaEventRepositoryPort;
import com.nursena.payflow.eventprocessing.application.port.out.TransferCompletedEventHandlerPort;
import com.nursena.payflow.eventprocessing.application.service.ProcessTransferCompletedEventService;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration(proxyBeanMethods = false)
@EnableKafka
@EnableConfigurationProperties(
    TransferCompletedKafkaConsumerProperties.class
)
class TransferCompletedKafkaConsumerConfiguration {

    @Bean("transferCompletedAuditEventProcessor")
    ProcessTransferCompletedEventUseCase
    transferCompletedAuditEventProcessor(
        ProcessedKafkaEventRepositoryPort repository,
        @Qualifier("transferCompletedAuditHandler")
        TransferCompletedEventHandlerPort handler,
        TransferCompletedKafkaConsumerProperties
            properties,
        Clock clock
    ) {
        return new ProcessTransferCompletedEventService(
            properties.consumerName(),
            repository,
            handler,
            clock
        );
    }
}

package com.nursena.payflow.outbox.adapter.out.kafka;

import com.nursena.payflow.outbox.application.port.out.OutboxMessagePublisherPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(
    OutboxKafkaPublisherProperties.class
)
class OutboxKafkaPublisherConfiguration {

    @Bean
    OutboxMessagePublisherPort
    outboxMessagePublisherPort(
        KafkaTemplate<String, String>
            kafkaTemplate,
        OutboxKafkaPublisherProperties
            properties
    ) {
        return new KafkaOutboxMessagePublisherAdapter(
            kafkaTemplate,
            properties.sendTimeout()
        );
    }
}

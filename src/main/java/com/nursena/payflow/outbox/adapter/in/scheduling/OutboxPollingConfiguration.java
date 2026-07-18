package com.nursena.payflow.outbox.adapter.in.scheduling;

import com.nursena.payflow.outbox.application.port.in.PublishOutboxEventsUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(
    OutboxPollingProperties.class
)
class OutboxPollingConfiguration {

    @Bean
    @ConditionalOnProperty(
        prefix = "payflow.outbox.polling",
        name = "enabled",
        havingValue = "true"
    )
    OutboxPublishingScheduler
    outboxPublishingScheduler(
        PublishOutboxEventsUseCase useCase,
        OutboxPollingProperties properties
    ) {
        return new OutboxPublishingScheduler(
            useCase,
            properties
        );
    }
}

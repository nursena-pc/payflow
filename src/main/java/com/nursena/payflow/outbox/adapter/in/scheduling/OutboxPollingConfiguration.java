package com.nursena.payflow.outbox.adapter.in.scheduling;

import com.nursena.payflow.outbox.application.port.out.OutboxBacklogQueryPort;
import com.nursena.payflow.outbox.application.port.in.PublishOutboxEventsUseCase;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;


@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(
    OutboxPollingProperties.class
)
@ConditionalOnProperty(
    prefix = "payflow.outbox.polling",
    name = "enabled",
    havingValue = "true"
)
class OutboxPollingConfiguration {
    @Bean
    OutboxBacklogMetrics outboxBacklogMetrics(
        OutboxBacklogQueryPort queryPort,
        MeterRegistry meterRegistry,
        Clock clock
    ) {
        return new OutboxBacklogMetrics(
            queryPort,
            meterRegistry,
            clock
        );
    }
    @Bean
    OutboxPublishingScheduler
    outboxPublishingScheduler(
        PublishOutboxEventsUseCase useCase,
        OutboxPollingMetrics metrics,
        OutboxBacklogMetrics backlogMetrics,
        OutboxPollingProperties properties
    ) {
        return new OutboxPublishingScheduler(
            useCase,
            metrics,
            backlogMetrics,
            properties
        );
    }
    @Bean
    OutboxPollingMetrics outboxPollingMetrics(
        MeterRegistry meterRegistry
    ) {
        return new OutboxPollingMetrics(
            meterRegistry
        );
    }
}

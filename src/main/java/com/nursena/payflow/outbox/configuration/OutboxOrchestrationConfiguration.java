package com.nursena.payflow.outbox.configuration;

import java.time.Clock;

import com.nursena.payflow.outbox.application.policy.OutboxRetryPolicy;
import com.nursena.payflow.outbox.application.port.in.PublishOutboxEventsUseCase;
import com.nursena.payflow.outbox.application.port.out.OutboxEventClaimPort;
import com.nursena.payflow.outbox.application.port.out.OutboxEventLifecyclePort;
import com.nursena.payflow.outbox.application.port.out.OutboxMessagePublisherPort;
import com.nursena.payflow.outbox.application.service.PublishOutboxEventsService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(
    OutboxRetryProperties.class
)
public class OutboxOrchestrationConfiguration {

    @Bean
    OutboxRetryPolicy outboxRetryPolicy(
        OutboxRetryProperties properties
    ) {
        return new OutboxRetryPolicy(
            properties.maxAttempts(),
            properties.initialDelay(),
            properties.maximumDelay()
        );
    }

    @Bean
    PublishOutboxEventsUseCase
    publishOutboxEventsUseCase(
        OutboxEventClaimPort claimPort,
        OutboxMessagePublisherPort publisherPort,
        OutboxEventLifecyclePort lifecyclePort,
        OutboxRetryPolicy retryPolicy,
        Clock clock
    ) {
        return new PublishOutboxEventsService(
            claimPort,
            publisherPort,
            lifecyclePort,
            retryPolicy,
            clock
        );
    }
}

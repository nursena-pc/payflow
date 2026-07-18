package com.nursena.payflow.outbox.configuration;

import java.time.Duration;

import com.nursena.payflow.outbox.application.policy.OutboxRetryPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OutboxOrchestrationConfiguration {

    private static final int MAX_ATTEMPTS = 5;

    private static final Duration INITIAL_RETRY_DELAY =
        Duration.ofSeconds(10);

    private static final Duration MAXIMUM_RETRY_DELAY =
        Duration.ofMinutes(1);

    @Bean
    OutboxRetryPolicy outboxRetryPolicy() {
        return new OutboxRetryPolicy(
            MAX_ATTEMPTS,
            INITIAL_RETRY_DELAY,
            MAXIMUM_RETRY_DELAY
        );
    }
}

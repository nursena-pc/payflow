package com.nursena.payflow.outbox.adapter.in.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.nursena.payflow.outbox.application.port.in.PublishOutboxEventsUseCase;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class OutboxPollingConfigurationTest {

    @Test
    void shouldNotCreatePollingBeansWhenDisabled() {
        contextRunner(false)
            .run(context -> {
                assertThat(context)
                    .doesNotHaveBean(
                        OutboxPublishingScheduler.class
                    );

                assertThat(context)
                    .doesNotHaveBean(
                        OutboxPollingProperties.class
                    );

                assertThat(context)
                    .doesNotHaveBean(
                        OutboxPollingMetrics.class
                    );
            });
    }

    @Test
    void shouldCreatePollingBeansWhenEnabled() {
        contextRunner(true)
            .run(context -> {
                assertThat(context)
                    .hasSingleBean(
                        OutboxPublishingScheduler.class
                    );

                assertThat(context)
                    .hasSingleBean(
                        OutboxPollingProperties.class
                    );

                assertThat(context)
                    .hasSingleBean(
                        OutboxPollingMetrics.class
                    );

                OutboxPollingProperties properties =
                    context.getBean(
                        OutboxPollingProperties.class
                    );

                assertThat(properties.publisherId())
                    .isEqualTo(
                        "publisher-context-test"
                    );

                assertThat(properties.batchSize())
                    .isEqualTo(25);
            });
    }

    private static ApplicationContextRunner contextRunner(
        boolean enabled
    ) {
        return new ApplicationContextRunner()
            .withUserConfiguration(
                OutboxPollingConfiguration.class
            )
            .withBean(
                PublishOutboxEventsUseCase.class,
                () -> mock(
                    PublishOutboxEventsUseCase.class
                )
            )
            .withBean(
                MeterRegistry.class,
                SimpleMeterRegistry::new
            )
            .withPropertyValues(
                "payflow.outbox.polling.enabled="
                    + enabled,
                "payflow.outbox.polling.publisher-id="
                    + "publisher-context-test",
                "payflow.outbox.polling.batch-size=25",
                "payflow.outbox.polling.lease-duration=30s",
                "payflow.outbox.polling.fixed-delay=1h",
                "payflow.outbox.polling.initial-delay=1h"
            );
    }
}

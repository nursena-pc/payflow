package com.nursena.payflow.maildelivery.adapter.in.scheduling;

import com.nursena.payflow.maildelivery.application.port.in.DispatchMailOutboxUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(MailOutboxPollingProperties.class)
@ConditionalOnProperty(
    prefix = "payflow.mail.outbox.polling",
    name = "enabled",
    havingValue = "true"
)
class MailOutboxPollingConfiguration {

    @Bean
    MailOutboxScheduler mailOutboxScheduler(
        DispatchMailOutboxUseCase useCase,
        MailOutboxPollingProperties properties
    ) {
        return new MailOutboxScheduler(useCase, properties);
    }
}

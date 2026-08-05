package com.nursena.payflow.maildelivery.configuration;

import java.time.Clock;

import com.nursena.payflow.maildelivery.adapter.out.smtp.MailDeliveryProperties;
import com.nursena.payflow.maildelivery.application.policy.MailRetryPolicy;
import com.nursena.payflow.maildelivery.application.port.in.DispatchMailOutboxUseCase;
import com.nursena.payflow.maildelivery.application.port.out.MailDeliveryPort;
import com.nursena.payflow.maildelivery.application.port.out.MailOutboxClaimPort;
import com.nursena.payflow.maildelivery.application.port.out.MailOutboxLifecyclePort;
import com.nursena.payflow.maildelivery.application.service.DispatchMailOutboxService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
    MailOutboxRetryProperties.class,
    MailDeliveryProperties.class
})
public class MailOutboxConfiguration {

    @Bean
    MailRetryPolicy mailRetryPolicy(
        MailOutboxRetryProperties properties
    ) {
        return new MailRetryPolicy(
            properties.maxAttempts(),
            properties.initialDelay(),
            properties.maximumDelay()
        );
    }

    @Bean
    DispatchMailOutboxUseCase dispatchMailOutboxUseCase(
        MailOutboxClaimPort claimPort,
        MailDeliveryPort deliveryPort,
        MailOutboxLifecyclePort lifecyclePort,
        MailRetryPolicy retryPolicy,
        Clock clock
    ) {
        return new DispatchMailOutboxService(
            claimPort,
            deliveryPort,
            lifecyclePort,
            retryPolicy,
            clock
        );
    }
}

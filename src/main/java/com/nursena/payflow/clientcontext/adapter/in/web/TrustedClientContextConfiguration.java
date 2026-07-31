package com.nursena.payflow.clientcontext.adapter.in.web;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(
    TrustedProxyProperties.class
)
class TrustedClientContextConfiguration {

    @Bean
    ClientAddressResolutionObserver
        clientAddressResolutionObserver(
            MeterRegistry meterRegistry
        ) {
        return new ClientAddressResolutionMetrics(
            meterRegistry
        );
    }

    @Bean
    ClientAddressResolver clientAddressResolver(
        TrustedProxyProperties properties,
        ClientAddressResolutionObserver observer
    ) {
        return new ServletClientAddressResolver(
            properties,
            observer
        );
    }
}

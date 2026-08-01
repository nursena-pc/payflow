package com.nursena.payflow.observability.adapter.in.web;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CorrelationIdOpenApiConfiguration {

    @Bean
    OpenApiCustomizer correlationIdOpenApiCustomizer() {
        return new CorrelationIdOpenApiCustomizer();
    }
}
package com.nursena.payflow.observability.adapter.in.web;

import java.util.EnumSet;

import com.nursena.payflow.observability.adapter.out.uuid.UuidCorrelationIdGenerator;
import com.nursena.payflow.observability.domain.CorrelationIdGenerator;
import com.nursena.payflow.observability.domain.CorrelationIdPolicy;

import jakarta.servlet.DispatcherType;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
class RequestCorrelationConfiguration {

    @Bean
    CorrelationIdPolicy correlationIdPolicy() {
        return new CorrelationIdPolicy();
    }

    @Bean
    CorrelationIdGenerator correlationIdGenerator() {
        return new UuidCorrelationIdGenerator();
    }

    @Bean
    FilterRegistrationBean<RequestCorrelationFilter>
        requestCorrelationFilter(
            CorrelationIdPolicy policy,
            CorrelationIdGenerator generator
        ) {
        RequestCorrelationFilter filter =
            new RequestCorrelationFilter(
                policy,
                generator
            );

        FilterRegistrationBean<RequestCorrelationFilter>
            registration =
                new FilterRegistrationBean<>(
                    filter
                );

        registration.setName(
            "requestCorrelationFilter"
        );

        registration.setOrder(
            Ordered.HIGHEST_PRECEDENCE
        );

        registration.setDispatcherTypes(
            EnumSet.of(
                DispatcherType.REQUEST
            )
        );

        registration.addUrlPatterns(
            "/*"
        );

        registration.setMatchAfter(
            false
        );

        return registration;
    }
}
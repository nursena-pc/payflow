package com.nursena.payflow.user.adapter.out.security;

import java.security.SecureRandom;

import com.nursena.payflow.user.application.port.out.StepUpGrantDigestPort;
import com.nursena.payflow.user.application.port.out.StepUpGrantGenerationPort;
import com.nursena.payflow.user.application.service.StepUpGrantLifetimePolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StepUpGrantProperties.class)
class StepUpGrantConfiguration {

    @Bean
    StepUpGrantGenerationPort stepUpGrantGenerationPort() {
        return new SecureRandomStepUpGrantGenerationAdapter(new SecureRandom());
    }

    @Bean
    StepUpGrantDigestPort stepUpGrantDigestPort() {
        return new Sha256StepUpGrantDigestAdapter();
    }

    @Bean
    StepUpGrantLifetimePolicy stepUpGrantLifetimePolicy(
        StepUpGrantProperties properties
    ) {
        return new StepUpGrantLifetimePolicy(properties.ttl());
    }
}

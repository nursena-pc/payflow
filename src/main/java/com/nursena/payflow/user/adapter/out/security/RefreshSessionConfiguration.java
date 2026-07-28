package com.nursena.payflow.user.adapter.out.security;

import com.nursena.payflow.user.application.service.RefreshSessionLifetimePolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(
    RefreshSessionProperties.class
)
class RefreshSessionConfiguration {

    @Bean
    RefreshSessionLifetimePolicy
    refreshSessionLifetimePolicy(
        RefreshSessionProperties properties
    ) {
        return new RefreshSessionLifetimePolicy(
            properties.refreshTokenTtl(),
            properties.familyTtl()
        );
    }
}

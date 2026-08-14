package com.nursena.payflow.abuseprotection.adapter.out.configuration;

import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionPolicyProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(
    AbuseProtectionProperties.class
)
class AbuseProtectionConfiguration {

    @Bean
    AbuseProtectionPolicyProvider
        abuseProtectionPolicyProvider(
            AbuseProtectionProperties properties
        ) {
        return new ConfiguredAbuseProtectionPolicyProvider(
            properties
        );
    }
}

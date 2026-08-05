package com.nursena.payflow.user.adapter.out.link;

import com.nursena.payflow.user.application.port.out
    .EmailVerificationLinkPort;
import org.springframework.boot.context.properties
    .EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(
    EmailVerificationLinkProperties.class
)
class EmailVerificationLinkConfiguration {

    @Bean
    EmailVerificationLinkPort emailVerificationLinkPort(
        EmailVerificationLinkProperties properties
    ) {
        return new ConfiguredEmailVerificationLinkAdapter(
            properties.emailVerificationConfirmationUri()
        );
    }
}

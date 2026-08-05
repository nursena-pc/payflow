package com.nursena.payflow.user.adapter.out.link;

import com.nursena.payflow.user.application.port.out
    .PasswordRecoveryLinkPort;
import org.springframework.boot.context.properties
    .EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(
    PasswordRecoveryLinkProperties.class
)
class PasswordRecoveryLinkConfiguration {

    @Bean
    PasswordRecoveryLinkPort passwordRecoveryLinkPort(
        PasswordRecoveryLinkProperties properties
    ) {
        return new ConfiguredPasswordRecoveryLinkAdapter(
            properties.passwordRecoveryConfirmationUri()
        );
    }
}

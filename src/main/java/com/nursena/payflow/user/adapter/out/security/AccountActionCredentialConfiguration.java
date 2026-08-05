package com.nursena.payflow.user.adapter.out.security;

import java.security.SecureRandom;

import com.nursena.payflow.user.application.port.out
    .AccountActionCredentialDigestPort;
import com.nursena.payflow.user.application.port.out
    .AccountActionCredentialGenerationPort;
import com.nursena.payflow.user.application.service
    .AccountActionCredentialLifetimePolicy;
import org.springframework.boot.context.properties
    .EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(
    AccountActionCredentialProperties.class
)
class AccountActionCredentialConfiguration {

    @Bean
    AccountActionCredentialGenerationPort
    accountActionCredentialGenerationPort() {
        return new
            SecureRandomAccountActionCredentialGenerationAdapter(
                new SecureRandom()
            );
    }

    @Bean
    AccountActionCredentialDigestPort
    accountActionCredentialDigestPort() {
        return new
            Sha256AccountActionCredentialDigestAdapter();
    }

    @Bean
    AccountActionCredentialLifetimePolicy
    accountActionCredentialLifetimePolicy(
        AccountActionCredentialProperties properties
    ) {
        return new AccountActionCredentialLifetimePolicy(
            properties.emailVerificationTtl(),
            properties.passwordRecoveryTtl()
        );
    }
}

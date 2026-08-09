package com.nursena.payflow.user.adapter.out.security;

import java.security.SecureRandom;

import com.nursena.payflow.user.application.port.out.MfaLoginChallengeDigestPort;
import com.nursena.payflow.user.application.port.out.MfaLoginChallengeGenerationPort;
import com.nursena.payflow.user.application.service.MfaLoginChallengeLifetimePolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MfaLoginChallengeProperties.class)
class MfaLoginChallengeConfiguration {

    @Bean
    MfaLoginChallengeGenerationPort mfaLoginChallengeGenerationPort() {
        return new SecureRandomMfaLoginChallengeGenerationAdapter(
            new SecureRandom()
        );
    }

    @Bean
    MfaLoginChallengeDigestPort mfaLoginChallengeDigestPort() {
        return new Sha256MfaLoginChallengeDigestAdapter();
    }

    @Bean
    MfaLoginChallengeLifetimePolicy mfaLoginChallengeLifetimePolicy(
        MfaLoginChallengeProperties properties
    ) {
        return new MfaLoginChallengeLifetimePolicy(
            properties.ttl(),
            properties.maxAttempts()
        );
    }
}

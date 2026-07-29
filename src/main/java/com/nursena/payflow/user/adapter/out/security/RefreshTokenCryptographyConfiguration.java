package com.nursena.payflow.user.adapter.out.security;

import java.security.SecureRandom;

import com.nursena.payflow.user.application.port.out.RefreshTokenDigestPort;
import com.nursena.payflow.user.application.port.out.RefreshTokenGenerationPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class RefreshTokenCryptographyConfiguration {

    @Bean
    RefreshTokenGenerationPort
    refreshTokenGenerationPort() {
        return new
            SecureRandomRefreshTokenGenerationAdapter(
            new SecureRandom()
        );
    }

    @Bean
    RefreshTokenDigestPort
    refreshTokenDigestPort() {
        return new
            Sha256RefreshTokenDigestAdapter();
    }
}

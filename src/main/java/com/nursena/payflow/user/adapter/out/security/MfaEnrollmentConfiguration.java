package com.nursena.payflow.user.adapter.out.security;

import com.nursena.payflow.user.application.port.out.TotpProvisioningUriPort;
import com.nursena.payflow.user.application.service.MfaEnrollmentLifetimePolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class MfaEnrollmentConfiguration {

    @Bean
    TotpProvisioningUriPort totpProvisioningUriPort(
        TotpProperties properties
    ) {
        return new ConfiguredTotpProvisioningUriAdapter(
            properties.issuer()
        );
    }

    @Bean
    MfaEnrollmentLifetimePolicy mfaEnrollmentLifetimePolicy(
        TotpProperties properties
    ) {
        return new MfaEnrollmentLifetimePolicy(
            properties.enrollmentTtl()
        );
    }
}

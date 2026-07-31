package com.nursena.payflow.clientcontext.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TrustedClientContextConfigurationTest {

    private final ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withUserConfiguration(
                TrustedClientContextConfiguration.class
            );

    @Test
    void shouldBindValidatedTrustedProxyConfiguration() {
        contextRunner
            .withPropertyValues(
                "payflow.security.client-context.trusted-proxy-cidrs[0]=10.0.0.0/8",
                "payflow.security.client-context.trusted-proxy-cidrs[1]=2001:db8::/32",
                "payflow.security.client-context.max-forwarded-header-length=4096",
                "payflow.security.client-context.max-forwarded-hops=16"
            )
            .run(context -> {
                assertThat(context)
                    .hasNotFailed()
                    .hasSingleBean(
                        TrustedProxyProperties.class
                    )
                    .hasSingleBean(
                        ServletClientAddressResolver.class
                    );

                TrustedProxyProperties properties =
                    context.getBean(
                        TrustedProxyProperties.class
                    );

                assertThat(
                    properties.trustedProxyCidrs()
                )
                    .containsExactly(
                        "10.0.0.0/8",
                        "2001:db8::/32"
                    );
            });
    }

    @Test
    void shouldFailContextForInvalidTrustedProxyNetwork() {
        contextRunner
            .withPropertyValues(
                "payflow.security.client-context.trusted-proxy-cidrs[0]=0.0.0.0/0",
                "payflow.security.client-context.max-forwarded-header-length=4096",
                "payflow.security.client-context.max-forwarded-hops=16"
            )
            .run(context -> {
                assertThat(context)
                    .hasFailed();

                assertThat(
                    context.getStartupFailure()
                )
                    .hasRootCauseInstanceOf(
                        IllegalArgumentException.class
                    );
            });
    }
}

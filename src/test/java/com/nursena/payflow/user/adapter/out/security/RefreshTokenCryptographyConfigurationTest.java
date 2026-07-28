package com.nursena.payflow.user.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nursena.payflow.user.application.port.out.GeneratedRefreshToken;
import com.nursena.payflow.user.application.port.out.RefreshTokenDigestPort;
import com.nursena.payflow.user.application.port.out.RefreshTokenGenerationPort;
import com.nursena.payflow.user.domain.model.RefreshTokenDigest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RefreshTokenCryptographyConfigurationTest {

    private final ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withUserConfiguration(
                RefreshTokenCryptographyConfiguration
                    .class
            );

    @Test
    void shouldExposeBothRefreshTokenOutputPorts() {
        contextRunner.run(context -> {
            assertThat(context)
                .hasSingleBean(
                    RefreshTokenGenerationPort.class
                );

            assertThat(context)
                .hasSingleBean(
                    RefreshTokenDigestPort.class
                );

            assertThat(
                context.getBean(
                    RefreshTokenGenerationPort.class
                )
            )
                .isInstanceOf(
                    SecureRandomRefreshTokenGenerationAdapter
                        .class
                );

            assertThat(
                context.getBean(
                    RefreshTokenDigestPort.class
                )
            )
                .isInstanceOf(
                    Sha256RefreshTokenDigestAdapter
                        .class
                );
        });
    }

    @Test
    void shouldGenerateAndDigestThroughConfiguredPorts() {
        contextRunner.run(context -> {
            RefreshTokenGenerationPort generationPort =
                context.getBean(
                    RefreshTokenGenerationPort.class
                );

            RefreshTokenDigestPort digestPort =
                context.getBean(
                    RefreshTokenDigestPort.class
                );

            GeneratedRefreshToken generated =
                generationPort.generate();

            RefreshTokenDigest digest =
                digestPort.digest(
                    generated.value()
                );

            assertThat(generated.value())
                .matches(
                    "[A-Za-z0-9_-]{43}"
                );

            assertThat(digest.value())
                .hasSize(
                    RefreshTokenDigest
                        .SHA_256_LENGTH_BYTES
                );

            assertThat(generated.toString())
                .doesNotContain(
                    generated.value()
                );

            assertThat(digest.toString())
                .isEqualTo(
                    "RefreshTokenDigest[redacted]"
                );
        });
    }
}

package com.nursena.payflow.user.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import com.nursena.payflow.user.application.service.RefreshSessionLifetimePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RefreshSessionConfigurationTest {

    private final ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withUserConfiguration(
                RefreshSessionConfiguration.class
            );

    @Test
    void shouldBindDurationsAndExposeLifetimePolicy() {
        contextRunner
            .withPropertyValues(
                "payflow.security.refresh-session."
                    + "refresh-token-ttl=7d",
                "payflow.security.refresh-session."
                    + "family-ttl=30d"
            )
            .run(context -> {
                assertThat(context)
                    .hasNotFailed();

                assertThat(context)
                    .hasSingleBean(
                        RefreshSessionProperties.class
                    );

                assertThat(context)
                    .hasSingleBean(
                        RefreshSessionLifetimePolicy.class
                    );

                RefreshSessionProperties properties =
                    context.getBean(
                        RefreshSessionProperties.class
                    );

                assertThat(
                    properties.refreshTokenTtl()
                )
                    .isEqualTo(
                        Duration.ofDays(7)
                    );

                assertThat(
                    properties.familyTtl()
                )
                    .isEqualTo(
                        Duration.ofDays(30)
                    );

                RefreshSessionLifetimePolicy policy =
                    context.getBean(
                        RefreshSessionLifetimePolicy.class
                    );

                Instant issuedAt =
                    Instant.parse(
                        "2026-07-28T12:00:00Z"
                    );

                assertThat(
                    policy.familyExpiresAt(
                        issuedAt
                    )
                )
                    .isEqualTo(
                        issuedAt.plus(
                            Duration.ofDays(30)
                        )
                    );
            });
    }

    @Test
    void shouldRejectNonPositiveConfiguration() {
        contextRunner
            .withPropertyValues(
                "payflow.security.refresh-session."
                    + "refresh-token-ttl=-1s",
                "payflow.security.refresh-session."
                    + "family-ttl=30d"
            )
            .run(context ->
                assertThat(context)
                    .hasFailed()
            );
    }
}

package com.nursena.payflow.user.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Map;

import com.nursena.payflow.configuration.TimeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JwtConfigurationClockTest {

    private final ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withUserConfiguration(
                TimeConfiguration.class,
                JwtConfiguration.class
            )
            .withPropertyValues(
                "payflow.security.jwt."
                    + "issuer=https://api.payflow.local",
                "payflow.security.jwt."
                    + "access-token-ttl=15m",
                "payflow.security.jwt.key-set."
                    + "provider-mode=ephemeral",
                "payflow.security.jwt.key-set."
                    + "active-key-id=clock-test"
            );

    @Test
    void shouldUseSingleSharedUtcClock() {
        contextRunner.run(context -> {
            assertThat(context)
                .hasNotFailed();

            Map<String, Clock> clocks =
                context.getBeansOfType(
                    Clock.class
                );

            assertThat(clocks)
                .containsOnlyKeys("clock");

            assertThat(
                context.containsBean(
                    "jwtClock"
                )
            )
                .isFalse();

            assertThat(
                context.getBean(
                    Clock.class
                ).getZone()
            )
                .isEqualTo(
                    ZoneOffset.UTC
                );
        });
    }
}

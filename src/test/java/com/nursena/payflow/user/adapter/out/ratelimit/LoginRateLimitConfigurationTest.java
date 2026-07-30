
package com.nursena.payflow.user.adapter.out.ratelimit;

import static org.assertj.core.api.Assertions
    .assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import com.nursena.payflow.user.application.port.out
    .LoginRateLimitPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple
    .SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner
    .ApplicationContextRunner;
import org.springframework.data.redis.connection
    .RedisConnectionFactory;
import org.springframework.data.redis.core
    .StringRedisTemplate;
import org.springframework.data.redis.core.script
    .RedisScript;

class LoginRateLimitConfigurationTest {

    private final ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withBean(
                StringRedisTemplate.class,
                () ->
                    new StringRedisTemplate(
                        mock(
                            RedisConnectionFactory.class
                        )
                    )
            )
            .withBean(
                MeterRegistry.class,
                SimpleMeterRegistry::new
            )
            .withUserConfiguration(
                LoginRateLimitConfiguration.class
            );

    @Test
    void shouldBindPropertiesAndExposeBeans() {
        contextRunner
            .withPropertyValues(
                "payflow.security.login-rate-limit."
                    + "enabled=true",
                "payflow.security.login-rate-limit."
                    + "window=15m",
                "payflow.security.login-rate-limit."
                    + "identity-limit=5",
                "payflow.security.login-rate-limit."
                    + "client-limit=20"
            )
            .run(context -> {
                assertThat(context)
                    .hasNotFailed()
                    .hasSingleBean(
                        LoginRateLimitProperties.class
                    )
                    .hasSingleBean(
                        LoginRateLimitPort.class
                    )
                    .hasSingleBean(
                        LoginRateLimitMetrics.class
                    )
                    .hasSingleBean(
                        RedisScript.class
                    );

                LoginRateLimitProperties properties =
                    context.getBean(
                        LoginRateLimitProperties.class
                    );

                assertThat(properties.enabled())
                    .isTrue();

                assertThat(properties.window())
                    .isEqualTo(
                        Duration.ofMinutes(15)
                    );

                RedisScript<?> script =
                    context.getBean(
                        RedisScript.class
                    );

                assertThat(
                    script.getScriptAsString()
                )
                    .contains(
                        "redis.call('INCR'",
                        "redis.call('EXPIRE'",
                        "redis.call('TTL'"
                    );
            });
    }

    @Test
    void shouldRejectInvalidConfiguration() {
        contextRunner
            .withPropertyValues(
                "payflow.security.login-rate-limit."
                    + "enabled=true",
                "payflow.security.login-rate-limit."
                    + "window=500ms",
                "payflow.security.login-rate-limit."
                    + "identity-limit=5",
                "payflow.security.login-rate-limit."
                    + "client-limit=20"
            )
            .run(context ->
                assertThat(context)
                    .hasFailed()
            );
    }
}

package com.nursena.payflow.abuseprotection.adapter.out.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionPolicyProvider;
import com.nursena.payflow.abuseprotection.application.port.out.AbuseProtectionEnforcementPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class AbuseProtectionRedisConfigurationTest {

    private final ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withBean(
                StringRedisTemplate.class,
                () -> mock(StringRedisTemplate.class)
            )
            .withBean(
                MeterRegistry.class,
                SimpleMeterRegistry::new
            )
            .withBean(
                AbuseProtectionPolicyProvider.class,
                () -> workflow -> null
            )
            .withUserConfiguration(
                AbuseProtectionRedisConfiguration.class
            );

    @Test
    void shouldExposeAtomicScriptAndEnforcementPort() {
        contextRunner.run(context -> {
            assertThat(context)
                .hasNotFailed()
                .hasSingleBean(
                    AbuseProtectionEnforcementPort.class
                )
                .hasSingleBean(AbuseProtectionMetrics.class)
                .hasSingleBean(RedisScript.class);

            RedisScript<?> script =
                context.getBean(RedisScript.class);

            assertThat(script.getScriptAsString())
                .contains(
                    "redis.call('INCR'",
                    "redis.call('EXPIRE'",
                    "redis.call('TTL'",
                    "return {"
                );
        });
    }
}

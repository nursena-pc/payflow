
package com.nursena.payflow.user.adapter.out.ratelimit;

import java.util.List;

import com.nursena.payflow.user.application.port.out
    .LoginRateLimitPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties
    .EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script
    .DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(
    LoginRateLimitProperties.class
)
class LoginRateLimitConfiguration {

    private static final String SCRIPT = """
        local window_seconds = tonumber(ARGV[1])
        local identity_limit = tonumber(ARGV[2])
        local client_limit = tonumber(ARGV[3])

        local identity_count = redis.call('INCR', KEYS[1])
        if identity_count == 1 then
            redis.call('EXPIRE', KEYS[1], window_seconds)
        end

        local client_count = redis.call('INCR', KEYS[2])
        if client_count == 1 then
            redis.call('EXPIRE', KEYS[2], window_seconds)
        end

        local identity_ttl = redis.call('TTL', KEYS[1])
        if identity_ttl < 0 then
            redis.call('EXPIRE', KEYS[1], window_seconds)
            identity_ttl = window_seconds
        end

        local client_ttl = redis.call('TTL', KEYS[2])
        if client_ttl < 0 then
            redis.call('EXPIRE', KEYS[2], window_seconds)
            client_ttl = window_seconds
        end

        local identity_blocked = 0
        if identity_count > identity_limit then
            identity_blocked = 1
        end

        local client_blocked = 0
        if client_count > client_limit then
            client_blocked = 1
        end

        local retry_after = 0
        if identity_blocked == 1 then
            retry_after = identity_ttl
        end

        if client_blocked == 1
            and client_ttl > retry_after then
            retry_after = client_ttl
        end

        return {
            identity_blocked,
            client_blocked,
            retry_after
        }
        """;

    @Bean
    @SuppressWarnings({
        "unchecked",
        "rawtypes"
    })
    RedisScript<List<Long>>
    loginRateLimitScript() {
        return (RedisScript) new DefaultRedisScript<>(
            SCRIPT,
            List.class
        );
    }

    @Bean
    LoginRateLimitMetrics loginRateLimitMetrics(
        MeterRegistry meterRegistry
    ) {
        return new LoginRateLimitMetrics(
            meterRegistry
        );
    }

    @Bean
    LoginRateLimitPort loginRateLimitPort(
        StringRedisTemplate redisTemplate,
        RedisScript<List<Long>> loginRateLimitScript,
        LoginRateLimitProperties properties,
        LoginRateLimitMetrics metrics
    ) {
        return new RedisLoginRateLimitAdapter(
            redisTemplate,
            loginRateLimitScript,
            properties,
            metrics
        );
    }
}

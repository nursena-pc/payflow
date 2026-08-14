package com.nursena.payflow.abuseprotection.adapter.out.redis;

import java.util.List;

import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionPolicyProvider;
import com.nursena.payflow.abuseprotection.application.port.out.AbuseProtectionEnforcementPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration(proxyBeanMethods = false)
class AbuseProtectionRedisConfiguration {

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

        if client_blocked == 1 and client_ttl > retry_after then
            retry_after = client_ttl
        end

        return {
            identity_blocked,
            client_blocked,
            retry_after
        }
        """;

    @Bean("abuseProtectionRedisScript")
    @SuppressWarnings({"unchecked", "rawtypes"})
    RedisScript<List<Long>> abuseProtectionRedisScript() {
        return (RedisScript) new DefaultRedisScript<>(
            SCRIPT,
            List.class
        );
    }

    @Bean
    AbuseProtectionEnforcementPort abuseProtectionEnforcementPort(
        StringRedisTemplate redisTemplate,
        @Qualifier("abuseProtectionRedisScript")
        RedisScript<List<Long>> script,
        AbuseProtectionPolicyProvider policyProvider
    ) {
        return new RedisAbuseProtectionAdapter(
            redisTemplate,
            script,
            policyProvider
        );
    }
}

package com.nursena.payflow.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class V015RedisAbuseEnforcementContractTest {

    private static final Path MAIN = Path.of(
        "src", "main", "java", "com", "nursena", "payflow"
    );

    @Test
    void shouldKeepApplicationPortFreeOfAdapterDependencies()
        throws IOException {

        Path portDirectory = MAIN.resolve(Path.of(
            "abuseprotection", "application", "port", "out"
        ));

        try (var paths = Files.list(portDirectory)) {
            for (Path path : paths.toList()) {
                assertThat(Files.readString(path))
                    .doesNotContain(
                        "org.springframework",
                        "jakarta.servlet",
                        "StringRedisTemplate",
                        "RedisScript"
                    );
            }
        }
    }

    @Test
    void shouldUseAtomicExpiringDigestOnlyRedisState()
        throws IOException {

        Path redisDirectory = MAIN.resolve(Path.of(
            "abuseprotection", "adapter", "out", "redis"
        ));

        String configuration = Files.readString(
            redisDirectory.resolve(
                "AbuseProtectionRedisConfiguration.java"
            )
        );

        String keyFactory = Files.readString(
            redisDirectory.resolve(
                "AbuseProtectionKeyFactory.java"
            )
        );

        assertThat(configuration)
            .contains(
                "redis.call('INCR'",
                "redis.call('EXPIRE'",
                "redis.call('TTL'",
                "identity_blocked",
                "client_blocked",
                "retry_after"
            );

        assertThat(keyFactory)
            .contains(
                "SHA-256",
                "payflow-abuse-protection-v1",
                "validatedWorkflow.configurationKey()",
                "identity",
                "client"
            );
    }

    @Test
    void shouldRetainExistingLoginLimiterContract()
        throws IOException {

        Path loginDirectory = MAIN.resolve(Path.of(
            "user", "adapter", "out", "ratelimit"
        ));

        assertThat(Files.readString(
            loginDirectory.resolve(
                "RedisLoginRateLimitAdapter.java"
            )
        )).contains(
            "implements LoginRateLimitPort",
            "resetIdentity",
            "LoginRateLimitMetrics"
        );

        assertThat(Files.readString(
            loginDirectory.resolve(
                "LoginRateLimitKeyFactory.java"
            )
        )).contains(
            "payflow:security:login:identity:",
            "payflow:security:login:client:"
        );
    }

    @Test
    void shouldRecordCompletedIncrementTwo()
        throws IOException {

        assertThat(Files.readString(
            Path.of("docs", "roadmap.md")
        )).contains(
            "- [x] Implement atomic Redis decisions with explicit expiration and bounded key cardinality",
            "- [x] Preserve existing login-rate-limit behavior while sharing only approved infrastructure",
            "Increment 2 is implemented by issue [#153]"
        );

        assertThat(Files.readString(
            Path.of("docs", "abuse-protection.md")
        )).contains(
            "## Redis state contract",
            "key suffixes are domain-separated 64-character SHA-256 digests",
            "ABUSE_PROTECTION_ENABLED` remains `false` by default"
        );
    }
}

package com.nursena.payflow.abuseprotection.adapter.out.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionFailureMode;
import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionPolicy;
import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionWorkflow;
import com.nursena.payflow.abuseprotection.application.port.out.AbuseProtectionDecision;
import com.nursena.payflow.abuseprotection.application.port.out.AbuseProtectionDimension;
import com.nursena.payflow.abuseprotection.application.port.out.AbuseProtectionRequest;
import com.nursena.payflow.clientcontext.domain.IpAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class RedisAbuseProtectionAdapterIntegrationTest {

    private static final int REDIS_PORT = 6379;

    @Container
    private static final GenericContainer<?> REDIS =
        new GenericContainer<>(
            DockerImageName.parse("redis:8-alpine")
        ).withExposedPorts(REDIS_PORT);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration configuration =
            new RedisStandaloneConfiguration(
                REDIS.getHost(),
                REDIS.getMappedPort(REDIS_PORT)
            );

        connectionFactory =
            new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();

        redisTemplate =
            new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        flushRedis();
    }

    @AfterEach
    void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void shouldApplyBothDimensionsAndReturnLongestTtl() {
        RedisAbuseProtectionAdapter adapter = adapter(
            Duration.ofSeconds(30),
            1,
            2
        );

        assertThat(adapter.evaluate(request("identity-a")).isAllowed())
            .isTrue();

        AbuseProtectionDecision identityBlocked =
            adapter.evaluate(request("identity-a"));

        assertThat(identityBlocked.blockedDimension())
            .isEqualTo(AbuseProtectionDimension.IDENTITY);

        AbuseProtectionDecision bothBlocked =
            adapter.evaluate(request("identity-a"));

        assertThat(bothBlocked.blockedDimension())
            .isEqualTo(AbuseProtectionDimension.BOTH);
        assertThat(bothBlocked.retryAfter()).isPositive();
    }

    @Test
    void shouldSetAndRepairPositiveExpiration() {
        RedisAbuseProtectionAdapter adapter = adapter(
            Duration.ofSeconds(20),
            5,
            20
        );

        AbuseProtectionRequest request = request("identity-a");
        String identityKey = AbuseProtectionKeyFactory.identityKey(
            request.workflow(),
            request.normalizedIdentity()
        );

        redisTemplate.opsForValue().set(identityKey, "1");

        assertThat(redisTemplate.getExpire(identityKey))
            .isEqualTo(-1L);

        adapter.evaluate(request);

        assertThat(
            redisTemplate.getExpire(
                identityKey,
                TimeUnit.SECONDS
            )
        ).isBetween(1L, 20L);
    }

    @Test
    void shouldOpenFreshWindowAfterExpiration()
        throws Exception {

        RedisAbuseProtectionAdapter adapter = adapter(
            Duration.ofSeconds(2),
            1,
            10
        );

        AbuseProtectionRequest request = request("identity-a");

        assertThat(adapter.evaluate(request).isAllowed())
            .isTrue();
        assertThat(adapter.evaluate(request).isAllowed())
            .isFalse();

        awaitExpiration(
            AbuseProtectionKeyFactory.identityKey(
                request.workflow(),
                request.normalizedIdentity()
            ),
            AbuseProtectionKeyFactory.clientKey(
                request.workflow(),
                request.effectiveClientAddress().value()
            )
        );

        assertThat(adapter.evaluate(request).isAllowed())
            .isTrue();
    }

    @Test
    void shouldRemainAtomicUnderConcurrentLoad()
        throws Exception {

        int operationCount = 24;
        int limit = 5;

        RedisAbuseProtectionAdapter adapter = adapter(
            Duration.ofSeconds(30),
            limit,
            100
        );

        AbuseProtectionRequest request = request("identity-a");
        ExecutorService executor =
            Executors.newFixedThreadPool(operationCount);
        CountDownLatch ready =
            new CountDownLatch(operationCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<AbuseProtectionDecision>> futures =
            new ArrayList<>();

        try {
            for (int index = 0; index < operationCount; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                            "Concurrent abuse decision start timed out."
                        );
                    }
                    return adapter.evaluate(request);
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS))
                .isTrue();
            start.countDown();

            long allowed = 0L;
            for (Future<AbuseProtectionDecision> future : futures) {
                if (future.get(10, TimeUnit.SECONDS).isAllowed()) {
                    allowed++;
                }
            }

            assertThat(allowed).isEqualTo(limit);

            String identityKey =
                AbuseProtectionKeyFactory.identityKey(
                    request.workflow(),
                    request.normalizedIdentity()
                );

            assertThat(redisTemplate.opsForValue().get(identityKey))
                .isEqualTo(Integer.toString(operationCount));
        } finally {
            executor.shutdownNow();
            assertThat(
                executor.awaitTermination(
                    10,
                    TimeUnit.SECONDS
                )
            ).isTrue();
        }
    }

    private RedisAbuseProtectionAdapter adapter(
        Duration window,
        int identityLimit,
        int clientLimit
    ) {
        AbuseProtectionPolicy policy =
            new AbuseProtectionPolicy(
                true,
                window,
                identityLimit,
                clientLimit,
                AbuseProtectionFailureMode.FAIL_CLOSED
            );

        return new RedisAbuseProtectionAdapter(
            redisTemplate,
            new AbuseProtectionRedisConfiguration()
                .abuseProtectionRedisScript(),
            workflow -> policy
        );
    }

    private static AbuseProtectionRequest request(
        String identity
    ) {
        return new AbuseProtectionRequest(
            AbuseProtectionWorkflow.REGISTRATION,
            identity,
            IpAddress.parse("203.0.113.10")
        );
    }

    private void flushRedis() {
        redisTemplate.execute(
            (RedisCallback<Void>) connection -> {
                connection.serverCommands().flushDb();
                return null;
            }
        );
    }

    private void awaitExpiration(String... keys)
        throws Exception {

        long deadline = System.nanoTime()
            + Duration.ofSeconds(6).toNanos();

        while (System.nanoTime() < deadline) {
            boolean anyPresent = false;

            for (String key : keys) {
                if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                    anyPresent = true;
                    break;
                }
            }

            if (!anyPresent) {
                return;
            }

            Thread.sleep(50L);
        }

        throw new AssertionError(
            "Redis abuse-protection keys did not expire in time."
        );
    }
}

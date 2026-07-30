package com.nursena.payflow.user.adapter.out.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.nursena.payflow.user.application.port.out
    .LoginRateLimitDecision;
import com.nursena.payflow.user.application.port.out
    .LoginRateLimitDimension;
import com.nursena.payflow.user.application.port.out
    .LoginRateLimitRequest;
import com.nursena.payflow.user.domain.model.EmailAddress;
import io.micrometer.core.instrument.simple
    .SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection
    .RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce
    .LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class RedisLoginRateLimitAdapterIntegrationTest {

    private static final int REDIS_PORT = 6379;

    private static final EmailAddress IDENTITY =
        EmailAddress.of(
            "nursena@example.com"
        );

    private static final String CLIENT_ADDRESS =
        "203.0.113.10";

    @Container
    private static final GenericContainer<?> REDIS =
        new GenericContainer<>(
            DockerImageName.parse(
                "redis:8-alpine"
            )
        )
            .withExposedPorts(REDIS_PORT);

    private LettuceConnectionFactory connectionFactory;

    private StringRedisTemplate redisTemplate;

    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration configuration =
            new RedisStandaloneConfiguration(
                REDIS.getHost(),
                REDIS.getMappedPort(
                    REDIS_PORT
                )
            );

        connectionFactory =
            new LettuceConnectionFactory(
                configuration
            );

        connectionFactory.afterPropertiesSet();
        connectionFactory.start();

        redisTemplate =
            new StringRedisTemplate(
                connectionFactory
            );

        redisTemplate.afterPropertiesSet();

        meterRegistry =
            new SimpleMeterRegistry();

        flushRedis();
    }

    @AfterEach
    void tearDown() {
        if (meterRegistry != null) {
            meterRegistry.close();
        }

        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void shouldApplyIdentityThresholdWithoutRefreshingTtl()
        throws Exception {

        RedisLoginRateLimitAdapter adapter =
            adapter(
                Duration.ofSeconds(8),
                2,
                100
            );

        LoginRateLimitRequest request =
            request(
                IDENTITY,
                CLIENT_ADDRESS
            );

        assertThat(
            adapter.evaluate(request)
                .isAllowed()
        )
            .isTrue();

        String identityKey =
            LoginRateLimitKeyFactory
                .identityKey(IDENTITY);

        String clientKey =
            LoginRateLimitKeyFactory
                .clientKey(CLIENT_ADDRESS);

        Long firstTtl =
            redisTemplate.getExpire(
                identityKey,
                TimeUnit.SECONDS
            );

        assertThat(firstTtl)
            .isBetween(1L, 8L);

        Thread.sleep(1_500L);

        assertThat(
            adapter.evaluate(request)
                .isAllowed()
        )
            .isTrue();

        Long secondTtl =
            redisTemplate.getExpire(
                identityKey,
                TimeUnit.SECONDS
            );

        assertThat(secondTtl)
            .isPositive()
            .isLessThan(firstTtl);

        LoginRateLimitDecision blocked =
            adapter.evaluate(request);

        assertThat(blocked.isAllowed())
            .isFalse();

        assertThat(
            blocked.blockedDimension()
        )
            .isEqualTo(
                LoginRateLimitDimension.IDENTITY
            );

        Long currentTtl =
            redisTemplate.getExpire(
                identityKey,
                TimeUnit.SECONDS
            );

        assertThat(
            blocked.retryAfter().toSeconds()
        )
            .isBetween(
                Math.max(
                    1L,
                    currentTtl - 1L
                ),
                currentTtl + 1L
            );

        assertThat(
            redisTemplate.opsForValue()
                .get(identityKey)
        )
            .isEqualTo("3");

        assertThat(
            redisTemplate.opsForValue()
                .get(clientKey)
        )
            .isEqualTo("3");
    }

    @Test
    void shouldBlockClientAcrossDistinctIdentities() {
        RedisLoginRateLimitAdapter adapter =
            adapter(
                Duration.ofSeconds(30),
                100,
                3
            );

        for (
            int attempt = 1;
            attempt <= 3;
            attempt++
        ) {
            assertThat(
                adapter.evaluate(
                    request(
                        EmailAddress.of(
                            "client-attempt-"
                                + attempt
                                + "@example.com"
                        ),
                        CLIENT_ADDRESS
                    )
                ).isAllowed()
            )
                .isTrue();
        }

        LoginRateLimitDecision blocked =
            adapter.evaluate(
                request(
                    EmailAddress.of(
                        "client-attempt-4@example.com"
                    ),
                    CLIENT_ADDRESS
                )
            );

        assertThat(blocked.isAllowed())
            .isFalse();

        assertThat(
            blocked.blockedDimension()
        )
            .isEqualTo(
                LoginRateLimitDimension.CLIENT
            );

        assertThat(
            redisTemplate.opsForValue()
                .get(
                    LoginRateLimitKeyFactory
                        .clientKey(
                            CLIENT_ADDRESS
                        )
                )
        )
            .isEqualTo("4");
    }

    @Test
    void shouldResetOnlyIdentityCounter() {
        RedisLoginRateLimitAdapter adapter =
            adapter(
                Duration.ofSeconds(30),
                5,
                20
            );

        LoginRateLimitRequest request =
            request(
                IDENTITY,
                CLIENT_ADDRESS
            );

        adapter.evaluate(request);
        adapter.evaluate(request);

        String identityKey =
            LoginRateLimitKeyFactory
                .identityKey(IDENTITY);

        String clientKey =
            LoginRateLimitKeyFactory
                .clientKey(CLIENT_ADDRESS);

        adapter.resetIdentity(IDENTITY);

        assertThat(
            redisTemplate.hasKey(
                identityKey
            )
        )
            .isFalse();

        assertThat(
            redisTemplate.opsForValue()
                .get(clientKey)
        )
            .isEqualTo("2");

        assertThat(
            redisTemplate.getExpire(
                clientKey,
                TimeUnit.SECONDS
            )
        )
            .isPositive();

        assertThat(
            adapter.evaluate(request)
                .isAllowed()
        )
            .isTrue();

        assertThat(
            redisTemplate.opsForValue()
                .get(identityKey)
        )
            .isEqualTo("1");

        assertThat(
            redisTemplate.opsForValue()
                .get(clientKey)
        )
            .isEqualTo("3");
    }

    @Test
    void shouldOpenANewWindowAfterExpiration()
        throws Exception {

        RedisLoginRateLimitAdapter adapter =
            adapter(
                Duration.ofSeconds(2),
                1,
                10
            );

        LoginRateLimitRequest request =
            request(
                IDENTITY,
                CLIENT_ADDRESS
            );

        assertThat(
            adapter.evaluate(request)
                .isAllowed()
        )
            .isTrue();

        assertThat(
            adapter.evaluate(request)
                .isAllowed()
        )
            .isFalse();

        String identityKey =
            LoginRateLimitKeyFactory
                .identityKey(IDENTITY);

        String clientKey =
            LoginRateLimitKeyFactory
                .clientKey(CLIENT_ADDRESS);

        awaitExpiration(
            identityKey,
            clientKey
        );

        assertThat(
            adapter.evaluate(request)
                .isAllowed()
        )
            .isTrue();

        assertThat(
            redisTemplate.opsForValue()
                .get(identityKey)
        )
            .isEqualTo("1");

        assertThat(
            redisTemplate.opsForValue()
                .get(clientKey)
        )
            .isEqualTo("1");
    }

    @Test
    void shouldApplyLuaAtomicallyUnderConcurrentLoad()
        throws Exception {

        int operationCount = 32;

        RedisLoginRateLimitAdapter adapter =
            adapter(
                Duration.ofSeconds(30),
                5,
                100
            );

        LoginRateLimitRequest request =
            request(
                IDENTITY,
                CLIENT_ADDRESS
            );

        ExecutorService executor =
            Executors.newFixedThreadPool(
                operationCount
            );

        CountDownLatch ready =
            new CountDownLatch(
                operationCount
            );

        CountDownLatch start =
            new CountDownLatch(1);

        List<Future<LoginRateLimitDecision>>
            futures =
            new ArrayList<>();

        try {
            for (
                int index = 0;
                index < operationCount;
                index++
            ) {
                futures.add(
                    executor.submit(
                        () -> {
                            ready.countDown();

                            if (
                                !start.await(
                                    10,
                                    TimeUnit.SECONDS
                                )
                            ) {
                                throw new
                                    IllegalStateException(
                                    "Concurrent login "
                                        + "rate-limit start "
                                        + "timed out."
                                );
                            }

                            return adapter.evaluate(
                                request
                            );
                        }
                    )
                );
            }

            assertThat(
                ready.await(
                    10,
                    TimeUnit.SECONDS
                )
            )
                .isTrue();

            start.countDown();

            long allowedCount = 0;
            long blockedCount = 0;

            for (
                Future<LoginRateLimitDecision>
                    future
                : futures
            ) {
                LoginRateLimitDecision decision =
                    future.get(
                        10,
                        TimeUnit.SECONDS
                    );

                if (decision.isAllowed()) {
                    allowedCount++;
                } else {
                    blockedCount++;

                    assertThat(
                        decision.blockedDimension()
                    )
                        .isEqualTo(
                            LoginRateLimitDimension
                                .IDENTITY
                        );
                }
            }

            assertThat(allowedCount)
                .isEqualTo(5);

            assertThat(blockedCount)
                .isEqualTo(
                    operationCount - 5L
                );

            assertThat(
                redisTemplate.opsForValue()
                    .get(
                        LoginRateLimitKeyFactory
                            .identityKey(
                                IDENTITY
                            )
                    )
            )
                .isEqualTo(
                    Integer.toString(
                        operationCount
                    )
                );

            assertThat(
                redisTemplate.opsForValue()
                    .get(
                        LoginRateLimitKeyFactory
                            .clientKey(
                                CLIENT_ADDRESS
                            )
                    )
            )
                .isEqualTo(
                    Integer.toString(
                        operationCount
                    )
                );
        } finally {
            executor.shutdownNow();

            assertThat(
                executor.awaitTermination(
                    10,
                    TimeUnit.SECONDS
                )
            )
                .isTrue();
        }
    }

    private RedisLoginRateLimitAdapter adapter(
        Duration window,
        int identityLimit,
        int clientLimit
    ) {
        return new RedisLoginRateLimitAdapter(
            redisTemplate,
            new LoginRateLimitConfiguration()
                .loginRateLimitScript(),
            new LoginRateLimitProperties(
                true,
                window,
                identityLimit,
                clientLimit
            ),
            new LoginRateLimitMetrics(
                meterRegistry
            )
        );
    }

    private void flushRedis() {
        redisTemplate.execute(
            (RedisCallback<Void>) connection -> {
                connection
                    .serverCommands()
                    .flushDb();

                return null;
            }
        );
    }

    private void awaitExpiration(
        String... keys
    ) throws Exception {
        long deadline =
            System.nanoTime()
                + Duration.ofSeconds(6)
                .toNanos();

        while (
            System.nanoTime() < deadline
        ) {
            boolean anyKeyPresent = false;

            for (String key : keys) {
                if (
                    Boolean.TRUE.equals(
                        redisTemplate.hasKey(
                            key
                        )
                    )
                ) {
                    anyKeyPresent = true;
                    break;
                }
            }

            if (!anyKeyPresent) {
                return;
            }

            Thread.sleep(50L);
        }

        throw new AssertionError(
            "Redis login rate-limit keys "
                + "did not expire in time."
        );
    }

    private static LoginRateLimitRequest request(
        EmailAddress identity,
        String clientAddress
    ) {
        return new LoginRateLimitRequest(
            identity,
            clientAddress
        );
    }
}

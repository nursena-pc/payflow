package com.nursena.payflow.user.integration;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nursena.payflow.user.application.port.out.RefreshTokenDigestPort;
import com.nursena.payflow.user.application.port.out.RefreshTokenRecordRepositoryPort;
import com.nursena.payflow.user.domain.model.RefreshTokenDigest;
import com.nursena.payflow.user.domain.model.RefreshTokenRecord;
import com.nursena.payflow.user.domain.model.RefreshTokenRecordId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    properties = {
        "payflow.security.refresh-session.refresh-token-ttl=7d",
        "payflow.security.refresh-session.family-ttl=30d"
    }
)
@AutoConfigureMockMvc
@Testcontainers
@Import(
    RotateRefreshCredentialsConcurrencyIntegrationTest
        .BlockingRepositoryConfiguration.class
)
class RotateRefreshCredentialsConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
            "postgres:17-alpine"
        );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RefreshTokenDigestPort
        refreshTokenDigest;

    @Autowired
    private BlockingRecordRepository
        recordRepository;

    @BeforeEach
    void setUp() {
        recordRepository.reset();

        jdbcTemplate.update(
            "DELETE FROM refresh_token_records"
        );

        jdbcTemplate.update(
            "DELETE FROM refresh_token_families"
        );

        jdbcTemplate.update(
            "DELETE FROM users"
        );
    }

    @Test
    void shouldAllowOnlyOneConcurrentRotation()
        throws Exception {

        String initialRefreshToken =
            issueInitialRefreshToken();

        recordRepository.holdNextLookup();

        ExecutorService executor =
            Executors.newFixedThreadPool(2);

        Future<MvcResult> firstFuture =
            null;

        Future<MvcResult> secondFuture =
            null;

        try {
            firstFuture =
                executor.submit(
                    () ->
                        performRefresh(
                            initialRefreshToken
                        )
                );

            assertThat(
                recordRepository
                    .awaitFirstLock()
            )
                .as(
                    "first request should acquire the refresh-token row lock"
                )
                .isTrue();

            secondFuture =
                executor.submit(
                    () ->
                        performRefresh(
                            initialRefreshToken
                        )
                );

            assertThat(
                recordRepository
                    .awaitSecondLookup()
            )
                .as(
                    "second request should reach the locked digest lookup"
                )
                .isTrue();

            Thread.sleep(300);

            assertThat(
                secondFuture.isDone()
            )
                .as(
                    "second request must remain blocked while the first transaction holds the row lock"
                )
                .isFalse();

            recordRepository.releaseFirstLookup();

            MvcResult firstResult =
                firstFuture.get(
                    15,
                    SECONDS
                );

            MvcResult secondResult =
                secondFuture.get(
                    15,
                    SECONDS
                );

            assertThat(
                firstResult
                    .getResponse()
                    .getStatus()
            )
                .isEqualTo(200);

            assertThat(
                secondResult
                    .getResponse()
                    .getStatus()
            )
                .isEqualTo(401);

            JsonNode successResponse =
                objectMapper.readTree(
                    firstResult
                        .getResponse()
                        .getContentAsByteArray()
                );

            JsonNode rejectedResponse =
                objectMapper.readTree(
                    secondResult
                        .getResponse()
                        .getContentAsByteArray()
                );

            assertThat(
                rejectedResponse
                    .path("code")
                    .asText()
            )
                .isEqualTo(
                    "REFRESH_TOKEN_INVALID"
                );

            String successorToken =
                successResponse
                    .path("refreshToken")
                    .asText();

            assertThat(successorToken)
                .isNotBlank()
                .isNotEqualTo(
                    initialRefreshToken
                );

            Integer recordCount =
                jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM refresh_token_records
                    """,
                    Integer.class
                );

            Integer consumedCount =
                jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM refresh_token_records
                    WHERE consumed_at IS NOT NULL
                      AND successor_id IS NOT NULL
                    """,
                    Integer.class
                );

            Integer activeCount =
                jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM refresh_token_records
                    WHERE consumed_at IS NULL
                      AND successor_id IS NULL
                    """,
                    Integer.class
                );

            byte[] successorDigest =
                jdbcTemplate.queryForObject(
                    """
                    SELECT token_digest
                    FROM refresh_token_records
                    WHERE token_digest = ?
                    """,
                    byte[].class,
                    refreshTokenDigest
                        .digest(successorToken)
                        .value()
                );

            assertThat(recordCount)
                .isEqualTo(2);

            assertThat(consumedCount)
                .isEqualTo(1);

            assertThat(activeCount)
                .isEqualTo(1);

            assertThat(successorDigest)
                .containsExactly(
                    refreshTokenDigest
                        .digest(successorToken)
                        .value()
                );

            assertThat(
                Arrays.equals(
                    successorDigest,
                    successorToken.getBytes(UTF_8)
                )
            )
                .isFalse();
        } finally {
            recordRepository
                .releaseFirstLookup();

            if (firstFuture != null) {
                firstFuture.cancel(true);
            }

            if (secondFuture != null) {
                secondFuture.cancel(true);
            }

            executor.shutdownNow();

            assertThat(
                executor.awaitTermination(
                    10,
                    SECONDS
                )
            )
                .as(
                    "concurrency executor should terminate cleanly"
                )
                .isTrue();
        }
    }

    private String issueInitialRefreshToken()
        throws Exception {

        Credentials credentials =
            new Credentials(
                "rotation-concurrency-"
                    + UUID.randomUUID()
                    + "@example.com",
                "StrongPassword123!"
            );

        mockMvc.perform(
                post("/api/v1/auth/register")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        objectMapper.writeValueAsString(
                            credentials
                        )
                    )
            )
            .andExpect(
                status().isCreated()
            );

        MvcResult loginResult =
            mockMvc.perform(
                    post("/api/v1/auth/login")
                        .contentType(
                            MediaType.APPLICATION_JSON
                        )
                        .content(
                            objectMapper.writeValueAsString(
                                credentials
                            )
                        )
                )
                .andExpect(
                    status().isOk()
                )
                .andReturn();

        JsonNode response =
            objectMapper.readTree(
                loginResult
                    .getResponse()
                    .getContentAsByteArray()
            );

        return response
            .path("refreshToken")
            .asText();
    }

    private MvcResult performRefresh(
        String refreshToken
    ) throws Exception {

        return mockMvc.perform(
                post("/api/v1/auth/refresh")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content(
                        objectMapper.writeValueAsString(
                            new RefreshRequest(
                                refreshToken
                            )
                        )
                    )
            )
            .andReturn();
    }

    private record Credentials(
        String email,
        String password
    ) {
    }

    private record RefreshRequest(
        String refreshToken
    ) {
    }

    @TestConfiguration(
        proxyBeanMethods = false
    )
    static class BlockingRepositoryConfiguration {

        @Bean
        @Primary
        BlockingRecordRepository
        blockingRotationRecordRepository(
            @Qualifier(
                "refreshTokenRecordPersistenceAdapter"
            )
            RefreshTokenRecordRepositoryPort delegate
        ) {
            return new BlockingRecordRepository(
                delegate
            );
        }
    }

    static final class BlockingRecordRepository
        implements RefreshTokenRecordRepositoryPort {

        private final RefreshTokenRecordRepositoryPort
            delegate;

        private final AtomicBoolean holdFirst =
            new AtomicBoolean();

        private final AtomicInteger lookupCount =
            new AtomicInteger();

        private volatile CountDownLatch
            firstLockAcquired =
            new CountDownLatch(0);

        private volatile CountDownLatch
            secondLookupStarted =
            new CountDownLatch(0);

        private volatile CountDownLatch
            releaseFirst =
            new CountDownLatch(0);

        BlockingRecordRepository(
            RefreshTokenRecordRepositoryPort delegate
        ) {
            this.delegate = delegate;
        }

        void holdNextLookup() {
            lookupCount.set(0);
            holdFirst.set(true);

            firstLockAcquired =
                new CountDownLatch(1);

            secondLookupStarted =
                new CountDownLatch(1);

            releaseFirst =
                new CountDownLatch(1);
        }

        boolean awaitFirstLock()
            throws InterruptedException {

            return firstLockAcquired.await(
                10,
                SECONDS
            );
        }

        boolean awaitSecondLookup()
            throws InterruptedException {

            return secondLookupStarted.await(
                10,
                SECONDS
            );
        }

        void releaseFirstLookup() {
            releaseFirst.countDown();
        }

        void reset() {
            holdFirst.set(false);
            lookupCount.set(0);
            firstLockAcquired.countDown();
            secondLookupStarted.countDown();
            releaseFirst.countDown();
        }

        @Override
        public RefreshTokenRecord save(
            RefreshTokenRecord record
        ) {
            return delegate.save(record);
        }

        @Override
        public Optional<RefreshTokenRecord>
        findByDigestForUpdate(
            RefreshTokenDigest digest
        ) {
            int invocation =
                lookupCount.incrementAndGet();

            if (invocation == 2) {
                secondLookupStarted
                    .countDown();
            }

            Optional<RefreshTokenRecord> result =
                delegate
                    .findByDigestForUpdate(
                        digest
                    );

            if (
                invocation == 1
                    && holdFirst.compareAndSet(
                        true,
                        false
                    )
            ) {
                firstLockAcquired
                    .countDown();

                awaitRelease();
            }

            return result;
        }

        @Override
        public Optional<RefreshTokenRecord>
        findById(
            RefreshTokenRecordId recordId
        ) {
            return delegate.findById(
                recordId
            );
        }

        private void awaitRelease() {
            try {
                boolean released =
                    releaseFirst.await(
                        15,
                        SECONDS
                    );

                if (!released) {
                    throw new IllegalStateException(
                        "first refresh-token lookup was not released"
                    );
                }
            } catch (
                InterruptedException exception
            ) {
                Thread.currentThread()
                    .interrupt();

                throw new IllegalStateException(
                    "refresh-token lookup wait was interrupted",
                    exception
                );
            }
        }
    }
}

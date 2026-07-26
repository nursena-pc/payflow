package com.nursena.payflow.user.adapter.out.persistence;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.nursena.payflow.user.application.port.out.RefreshTokenFamilyRepositoryPort;
import com.nursena.payflow.user.application.port.out.RefreshTokenRecordRepositoryPort;
import com.nursena.payflow.user.domain.model.RefreshTokenDigest;
import com.nursena.payflow.user.domain.model.RefreshTokenFamily;
import com.nursena.payflow.user.domain.model.RefreshTokenFamilyId;
import com.nursena.payflow.user.domain.model.RefreshTokenRecord;
import com.nursena.payflow.user.domain.model.RefreshTokenRecordId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class RefreshTokenPersistenceLockIntegrationTest {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
            "postgres:17-alpine"
        );

    @Autowired
    private RefreshTokenFamilyRepositoryPort
        familyRepository;

    @Autowired
    private RefreshTokenRecordRepositoryPort
        recordRepository;

    @Autowired
    private PlatformTransactionManager
        transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
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
    void shouldBlockConcurrentFamilyLockUntilFirstTransactionCompletes()
        throws Exception {

        UUID userId =
            insertUser();

        Instant createdAt =
            Instant.parse(
                "2026-07-26T18:00:00Z"
            );

        RefreshTokenFamily family =
            familyRepository.save(
                RefreshTokenFamily.create(
                    RefreshTokenFamilyId.of(
                        UUID.randomUUID()
                    ),
                    userId,
                    createdAt,
                    createdAt.plusSeconds(
                        86_400
                    )
                )
            );

        assertSecondTransactionWaits(
            () ->
                familyRepository
                    .findByIdForUpdate(
                        family.id()
                    )
                    .orElseThrow()
        );
    }

    @Test
    void shouldBlockConcurrentDigestLockUntilFirstTransactionCompletes()
        throws Exception {

        UUID userId =
            insertUser();

        Instant createdAt =
            Instant.parse(
                "2026-07-26T18:00:00Z"
            );

        Instant expiresAt =
            createdAt.plusSeconds(
                86_400
            );

        RefreshTokenFamily family =
            familyRepository.save(
                RefreshTokenFamily.create(
                    RefreshTokenFamilyId.of(
                        UUID.randomUUID()
                    ),
                    userId,
                    createdAt,
                    expiresAt
                )
            );

        RefreshTokenDigest digest =
            digest(1);

        recordRepository.save(
            RefreshTokenRecord.issue(
                RefreshTokenRecordId.of(
                    UUID.randomUUID()
                ),
                family,
                digest,
                createdAt.plusSeconds(60),
                expiresAt
            )
        );

        assertSecondTransactionWaits(
            () ->
                recordRepository
                    .findByDigestForUpdate(
                        digest
                    )
                    .orElseThrow()
        );
    }

    private void assertSecondTransactionWaits(
        Runnable lockOperation
    ) throws Exception {

        ExecutorService executor =
            Executors.newFixedThreadPool(2);

        CountDownLatch holderHasLock =
            new CountDownLatch(1);

        CountDownLatch releaseHolder =
            new CountDownLatch(1);

        String identifier =
            UUID.randomUUID()
                .toString()
                .replace("-", "");

        String holderApplicationName =
            "refresh-lock-holder-"
                + identifier;

        String contenderApplicationName =
            "refresh-lock-contender-"
                + identifier;

        Future<?> holderFuture =
            null;

        Future<?> contenderFuture =
            null;

        try {
            holderFuture =
                executor.submit(
                    () ->
                        executeInTransaction(
                            () -> {
                                setApplicationName(
                                    holderApplicationName
                                );

                                lockOperation.run();

                                holderHasLock.countDown();

                                await(
                                    releaseHolder
                                );
                            }
                        )
                );

            assertThat(
                holderHasLock.await(
                    10,
                    SECONDS
                )
            )
                .as(
                    "first transaction should acquire the lock"
                )
                .isTrue();

            contenderFuture =
                executor.submit(
                    () ->
                        executeInTransaction(
                            () -> {
                                setApplicationName(
                                    contenderApplicationName
                                );

                                lockOperation.run();
                            }
                        )
                );

            assertThat(
                waitUntilLockWaitVisible(
                    contenderApplicationName,
                    Duration.ofSeconds(10)
                )
            )
                .as(
                    "second transaction should wait on a PostgreSQL lock"
                )
                .isTrue();

            assertThat(
                contenderFuture.isDone()
            )
                .as(
                    "second transaction must remain blocked before release"
                )
                .isFalse();

            releaseHolder.countDown();

            holderFuture.get(
                10,
                SECONDS
            );

            contenderFuture.get(
                10,
                SECONDS
            );

            assertThat(
                contenderFuture.isDone()
            )
                .isTrue();
        } finally {
            releaseHolder.countDown();

            if (holderFuture != null) {
                holderFuture.cancel(true);
            }

            if (contenderFuture != null) {
                contenderFuture.cancel(true);
            }

            executor.shutdownNow();

            assertThat(
                executor.awaitTermination(
                    10,
                    SECONDS
                )
            )
                .as(
                    "executor should terminate cleanly"
                )
                .isTrue();
        }
    }

    private void executeInTransaction(
        Runnable action
    ) {
        TransactionTemplate template =
            new TransactionTemplate(
                transactionManager
            );

        template.executeWithoutResult(
            status -> action.run()
        );
    }

    private void setApplicationName(
        String applicationName
    ) {
        jdbcTemplate.queryForObject(
            """
            SELECT set_config(
                'application_name',
                ?,
                TRUE
            )
            """,
            String.class,
            applicationName
        );
    }

    private boolean waitUntilLockWaitVisible(
        String applicationName,
        Duration timeout
    ) {
        Instant deadline =
            Instant.now().plus(timeout);

        while (
            Instant.now()
                .isBefore(deadline)
        ) {
            Boolean waiting =
                jdbcTemplate.queryForObject(
                    """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_stat_activity
                        WHERE application_name = ?
                          AND wait_event_type = 'Lock'
                    )
                    """,
                    Boolean.class,
                    applicationName
                );

            if (Boolean.TRUE.equals(waiting)) {
                return true;
            }

            sleepBriefly();
        }

        return false;
    }

    private static void await(
        CountDownLatch latch
    ) {
        try {
            boolean released =
                latch.await(
                    15,
                    SECONDS
                );

            if (!released) {
                throw new IllegalStateException(
                    "lock holder was not released"
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread()
                .interrupt();

            throw new IllegalStateException(
                "lock wait was interrupted",
                exception
            );
        }
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException exception) {
            Thread.currentThread()
                .interrupt();

            throw new IllegalStateException(
                "lock polling was interrupted",
                exception
            );
        }
    }

    private UUID insertUser() {
        UUID userId =
            UUID.randomUUID();

        Instant now =
            Instant.parse(
                "2026-07-26T18:00:00Z"
            );

        Timestamp timestamp =
            Timestamp.from(now);

        jdbcTemplate.update(
            """
            INSERT INTO users (
                id,
                email,
                password_hash,
                role,
                status,
                created_at,
                updated_at
            )
            VALUES (?, ?, ?, 'USER', 'ACTIVE', ?, ?)
            """,
            userId,
            userId + "@example.com",
            "test-password-hash",
            timestamp,
            timestamp
        );

        return userId;
    }

    private static RefreshTokenDigest digest(
        int marker
    ) {
        byte[] value =
            new byte[
                RefreshTokenDigest
                    .SHA_256_LENGTH_BYTES
            ];

        Arrays.fill(
            value,
            (byte) marker
        );

        return RefreshTokenDigest.of(
            value
        );
    }
}

package com.nursena.payflow.user.application.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions
    .assertThat;
import static org.assertj.core.api.Assertions
    .assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.nursena.payflow.user.application.port.out
    .AccountActionCredentialRepositoryPort;
import com.nursena.payflow.user.domain.exception
    .InvalidAccountActionCredentialException;
import com.nursena.payflow.user.domain.model
    .AccountActionCredentialPurpose;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection
    .ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction
    .PlatformTransactionManager;
import org.springframework.transaction.support
    .TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class AccountActionCredentialTransactionIntegrationTest {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private AccountActionCredentialIssuer issuer;

    @Autowired
    private AccountActionCredentialConsumer consumer;

    @Autowired
    private AccountActionCredentialRepositoryPort
        credentialRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ExecutorService executor;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update(
            "DELETE FROM account_action_credentials"
        );
        jdbcTemplate.update("DELETE FROM users");
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void stopExecutor() throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(10, SECONDS))
            .isTrue();
    }

    @Test
    void shouldPersistOnlyDigestAndSupersedePriorCredential() {
        UUID userId = insertUser();

        IssuedAccountActionCredential first =
            issuer.issue(
                userId,
                AccountActionCredentialPurpose
                    .EMAIL_VERIFICATION
            );
        IssuedAccountActionCredential second =
            issuer.issue(
                userId,
                AccountActionCredentialPurpose
                    .EMAIL_VERIFICATION
            );

        assertThat(first.value())
            .isNotEqualTo(second.value());
        assertThat(unresolvedCount(userId))
            .isEqualTo(1);
        assertThat(supersededCount(userId))
            .isEqualTo(1);

        List<String> persistedDigests =
            jdbcTemplate.queryForList(
                """
                SELECT encode(credential_digest, 'hex')
                FROM account_action_credentials
                WHERE user_id = ?
                """,
                String.class,
                userId
            );

        assertThat(persistedDigests)
            .hasSize(2)
            .allSatisfy(value ->
                assertThat(value).hasSize(64)
            )
            .noneMatch(value ->
                value.contains(first.value())
                    || value.contains(second.value())
            );
    }

    @Test
    void shouldPermitOneConcurrentConsumptionWinner()
        throws Exception {

        UUID userId = insertUser();
        IssuedAccountActionCredential issued =
            issuer.issue(
                userId,
                AccountActionCredentialPurpose
                    .PASSWORD_RECOVERY
            );
        CountDownLatch start = new CountDownLatch(1);

        Future<Boolean> first = executor.submit(() ->
            consumeWhenReleased(issued.value(), start)
        );
        Future<Boolean> second = executor.submit(() ->
            consumeWhenReleased(issued.value(), start)
        );

        start.countDown();

        assertThat(
            List.of(
                first.get(15, SECONDS),
                second.get(15, SECONDS)
            )
        )
            .containsExactlyInAnyOrder(true, false);
        assertThat(consumedCount(userId))
            .isEqualTo(1);
    }

    @Test
    void shouldSerializeConcurrentIssuancePerUserAndPurpose()
        throws Exception {

        UUID userId = insertUser();
        CountDownLatch start = new CountDownLatch(1);

        Future<IssuedAccountActionCredential> first =
            executor.submit(() -> {
                await(start);
                return issuer.issue(
                    userId,
                    AccountActionCredentialPurpose
                        .EMAIL_VERIFICATION
                );
            });
        Future<IssuedAccountActionCredential> second =
            executor.submit(() -> {
                await(start);
                return issuer.issue(
                    userId,
                    AccountActionCredentialPurpose
                        .EMAIL_VERIFICATION
                );
            });

        start.countDown();

        assertThat(first.get(15, SECONDS).value())
            .isNotEqualTo(
                second.get(15, SECONDS).value()
            );
        assertThat(unresolvedCount(userId))
            .isEqualTo(1);
        assertThat(supersededCount(userId))
            .isEqualTo(1);
    }

    @Test
    void shouldRollbackSupersessionWhenTransactionFails() {
        UUID userId = insertUser();
        issuer.issue(
            userId,
            AccountActionCredentialPurpose
                .EMAIL_VERIFICATION
        );

        TransactionTemplate transaction =
            new TransactionTemplate(transactionManager);

        assertThatThrownBy(() ->
            transaction.executeWithoutResult(status -> {
                credentialRepository.supersedeUnresolved(
                    userId,
                    AccountActionCredentialPurpose
                        .EMAIL_VERIFICATION,
                    Instant.now()
                );
                throw new IllegalStateException(
                    "forced rollback"
                );
            })
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("forced rollback");

        assertThat(unresolvedCount(userId))
            .isEqualTo(1);
        assertThat(supersededCount(userId))
            .isZero();
    }

    @Test
    void shouldRejectCredentialUsedForWrongPurpose() {
        UUID userId = insertUser();
        IssuedAccountActionCredential issued =
            issuer.issue(
                userId,
                AccountActionCredentialPurpose
                    .EMAIL_VERIFICATION
            );

        assertThatThrownBy(() ->
            consumer.consume(
                issued.value(),
                AccountActionCredentialPurpose
                    .PASSWORD_RECOVERY
            )
        )
            .isInstanceOf(
                InvalidAccountActionCredentialException.class
            )
            .hasMessage(
                "Account action credential is invalid."
            );
        assertThat(unresolvedCount(userId))
            .isEqualTo(1);
    }

    private boolean consumeWhenReleased(
        String credential,
        CountDownLatch start
    ) throws InterruptedException {
        await(start);

        try {
            consumer.consume(
                credential,
                AccountActionCredentialPurpose
                    .PASSWORD_RECOVERY
            );
            return true;
        } catch (
            InvalidAccountActionCredentialException ignored
        ) {
            return false;
        }
    }

    private static void await(
        CountDownLatch latch
    ) throws InterruptedException {
        if (!latch.await(10, SECONDS)) {
            throw new IllegalStateException(
                "concurrent operation was not released"
            );
        }
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.parse(
            "2026-08-05T10:00:00Z"
        );
        Timestamp timestamp = Timestamp.from(now);

        jdbcTemplate.update(
            """
            INSERT INTO users (
                id,
                email,
                password_hash,
                role,
                status,
                email_verified_at,
                created_at,
                updated_at
            )
            VALUES (?, ?, ?, 'USER', 'ACTIVE', ?, ?, ?)
            """,
            userId,
            userId + "@example.com",
            "test-password-hash",
            timestamp,
            timestamp,
            timestamp
        );

        return userId;
    }

    private int unresolvedCount(UUID userId) {
        return queryCount(
            """
            SELECT COUNT(*)
            FROM account_action_credentials
            WHERE user_id = ?
              AND consumed_at IS NULL
              AND superseded_at IS NULL
            """,
            userId
        );
    }

    private int supersededCount(UUID userId) {
        return queryCount(
            """
            SELECT COUNT(*)
            FROM account_action_credentials
            WHERE user_id = ?
              AND superseded_at IS NOT NULL
            """,
            userId
        );
    }

    private int consumedCount(UUID userId) {
        return queryCount(
            """
            SELECT COUNT(*)
            FROM account_action_credentials
            WHERE user_id = ?
              AND consumed_at IS NOT NULL
            """,
            userId
        );
    }

    private int queryCount(
        String sql,
        UUID userId
    ) {
        Integer result = jdbcTemplate.queryForObject(
            sql,
            Integer.class,
            userId
        );

        return result == null ? 0 : result;
    }
}

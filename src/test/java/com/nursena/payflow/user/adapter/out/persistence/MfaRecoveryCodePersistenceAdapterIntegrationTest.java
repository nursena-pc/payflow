package com.nursena.payflow.user.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import com.nursena.payflow.user.application.port.out
    .MfaRecoveryCodeRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection
    .ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class MfaRecoveryCodePersistenceAdapterIntegrationTest {

    private static final Instant NOW =
        Instant.parse("2026-08-11T12:00:00Z");

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MfaRecoveryCodeRepositoryPort repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM mfa_recovery_codes");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void shouldDeleteAllCodesForOnlyRequestedUser() {
        UUID targetUserId = insertUser();
        UUID otherUserId = insertUser();

        insertRecoveryCode(targetUserId, digest((byte) 1));
        insertRecoveryCode(targetUserId, digest((byte) 2));
        insertRecoveryCode(otherUserId, digest((byte) 3));

        TransactionTemplate transaction =
            new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status ->
            repository.deleteAllByUserId(targetUserId)
        );

        assertThat(recoveryCodeCount(targetUserId))
            .isZero();
        assertThat(recoveryCodeCount(otherUserId))
            .isEqualTo(1);
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        Timestamp timestamp = Timestamp.from(NOW);

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
            VALUES (?, ?, 'test-password-hash',
                    'USER', 'ACTIVE', ?, ?, ?)
            """,
            userId,
            userId + "@example.com",
            timestamp,
            timestamp,
            timestamp
        );

        return userId;
    }

    private void insertRecoveryCode(
        UUID userId,
        byte[] digest
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO mfa_recovery_codes (
                id,
                user_id,
                code_digest,
                created_at,
                consumed_at
            )
            VALUES (?, ?, ?, ?, NULL)
            """,
            UUID.randomUUID(),
            userId,
            digest,
            Timestamp.from(NOW)
        );
    }

    private int recoveryCodeCount(UUID userId) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM mfa_recovery_codes
            WHERE user_id = ?
            """,
            Integer.class,
            userId
        );

        return count == null ? 0 : count;
    }

    private static byte[] digest(byte value) {
        byte[] digest = new byte[32];
        Arrays.fill(digest, value);
        return digest;
    }
}

package com.nursena.payflow.user.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection
    .ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class RefreshSessionSchemaIntegrationTest {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
            "postgres:17-alpine"
        );

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
    void shouldPersistValidRotationLineage() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        UUID predecessorId = UUID.randomUUID();
        UUID successorId = UUID.randomUUID();

        Instant familyCreatedAt =
            Instant.parse(
                "2026-07-26T18:00:00Z"
            );

        Instant familyExpiresAt =
            Instant.parse(
                "2026-08-25T18:00:00Z"
            );

        Instant successorIssuedAt =
            Instant.parse(
                "2026-07-26T19:00:00Z"
            );

        insertUser(userId);

        insertFamily(
            familyId,
            userId,
            familyCreatedAt,
            familyExpiresAt
        );

        insertToken(
            successorId,
            familyId,
            digest(2),
            successorIssuedAt,
            familyExpiresAt,
            null,
            null
        );

        insertToken(
            predecessorId,
            familyId,
            digest(1),
            familyCreatedAt.plusSeconds(60),
            familyExpiresAt,
            successorIssuedAt,
            successorId
        );

        Integer familyCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM refresh_token_families
                WHERE id = ?
                """,
                Integer.class,
                familyId
            );

        Integer tokenCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM refresh_token_records
                WHERE family_id = ?
                """,
                Integer.class,
                familyId
            );

        UUID persistedSuccessor =
            jdbcTemplate.queryForObject(
                """
                SELECT successor_id
                FROM refresh_token_records
                WHERE id = ?
                """,
                UUID.class,
                predecessorId
            );

        assertThat(familyCount)
            .isEqualTo(1);

        assertThat(tokenCount)
            .isEqualTo(2);

        assertThat(persistedSuccessor)
            .isEqualTo(successorId);
    }

    @Test
    void shouldRejectDigestThatIsNotSha256Length() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();

        Instant createdAt =
            Instant.parse(
                "2026-07-26T18:00:00Z"
            );

        Instant expiresAt =
            createdAt.plusSeconds(86_400);

        insertUser(userId);

        insertFamily(
            familyId,
            userId,
            createdAt,
            expiresAt
        );

        assertThatThrownBy(
            () -> insertToken(
                UUID.randomUUID(),
                familyId,
                new byte[31],
                createdAt.plusSeconds(60),
                expiresAt,
                null,
                null
            )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    @Test
    void shouldRejectDuplicateDigest() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();

        Instant createdAt =
            Instant.parse(
                "2026-07-26T18:00:00Z"
            );

        Instant expiresAt =
            createdAt.plusSeconds(86_400);

        byte[] digest = digest(3);

        insertUser(userId);

        insertFamily(
            familyId,
            userId,
            createdAt,
            expiresAt
        );

        insertToken(
            UUID.randomUUID(),
            familyId,
            digest,
            createdAt.plusSeconds(60),
            expiresAt,
            null,
            null
        );

        assertThatThrownBy(
            () -> insertToken(
                UUID.randomUUID(),
                familyId,
                digest,
                createdAt.plusSeconds(120),
                expiresAt,
                null,
                null
            )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    @Test
    void shouldRejectPartialFamilyRevocationState() {
        UUID userId = UUID.randomUUID();

        Instant createdAt =
            Instant.parse(
                "2026-07-26T18:00:00Z"
            );

        Instant expiresAt =
            createdAt.plusSeconds(86_400);

        insertUser(userId);

        assertThatThrownBy(
            () -> jdbcTemplate.update(
                """
                INSERT INTO refresh_token_families (
                    id,
                    user_id,
                    created_at,
                    expires_at,
                    revoked_at,
                    revocation_reason
                )
                VALUES (?, ?, ?, ?, ?, NULL)
                """,
                UUID.randomUUID(),
                userId,
                timestamp(createdAt),
                timestamp(expiresAt),
                timestamp(
                    createdAt.plusSeconds(60)
                )
            )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );

        assertThatThrownBy(
            () -> jdbcTemplate.update(
                """
                INSERT INTO refresh_token_families (
                    id,
                    user_id,
                    created_at,
                    expires_at,
                    revoked_at,
                    revocation_reason
                )
                VALUES (?, ?, ?, ?, NULL, ?)
                """,
                UUID.randomUUID(),
                userId,
                timestamp(createdAt),
                timestamp(expiresAt),
                "CURRENT_SESSION_LOGOUT"
            )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    @Test
    void shouldRejectPartialTokenConsumptionState() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        UUID successorId = UUID.randomUUID();

        Instant createdAt =
            Instant.parse(
                "2026-07-26T18:00:00Z"
            );

        Instant expiresAt =
            createdAt.plusSeconds(86_400);

        insertUser(userId);

        insertFamily(
            familyId,
            userId,
            createdAt,
            expiresAt
        );

        insertToken(
            successorId,
            familyId,
            digest(4),
            createdAt.plusSeconds(3_600),
            expiresAt,
            null,
            null
        );

        assertThatThrownBy(
            () -> insertToken(
                UUID.randomUUID(),
                familyId,
                digest(5),
                createdAt.plusSeconds(60),
                expiresAt,
                createdAt.plusSeconds(3_600),
                null
            )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );

        assertThatThrownBy(
            () -> insertToken(
                UUID.randomUUID(),
                familyId,
                digest(6),
                createdAt.plusSeconds(120),
                expiresAt,
                null,
                successorId
            )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    @Test
    void shouldRejectSelfSuccessor() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();

        Instant createdAt =
            Instant.parse(
                "2026-07-26T18:00:00Z"
            );

        Instant expiresAt =
            createdAt.plusSeconds(86_400);

        insertUser(userId);

        insertFamily(
            familyId,
            userId,
            createdAt,
            expiresAt
        );

        assertThatThrownBy(
            () -> insertToken(
                tokenId,
                familyId,
                digest(7),
                createdAt.plusSeconds(60),
                expiresAt,
                createdAt.plusSeconds(3_600),
                tokenId
            )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    @Test
    void shouldRejectCrossFamilySuccessor() {
        UUID userId = UUID.randomUUID();
        UUID firstFamilyId = UUID.randomUUID();
        UUID secondFamilyId = UUID.randomUUID();
        UUID successorId = UUID.randomUUID();

        Instant createdAt =
            Instant.parse(
                "2026-07-26T18:00:00Z"
            );

        Instant expiresAt =
            createdAt.plusSeconds(86_400);

        insertUser(userId);

        insertFamily(
            firstFamilyId,
            userId,
            createdAt,
            expiresAt
        );

        insertFamily(
            secondFamilyId,
            userId,
            createdAt,
            expiresAt
        );

        insertToken(
            successorId,
            secondFamilyId,
            digest(8),
            createdAt.plusSeconds(3_600),
            expiresAt,
            null,
            null
        );

        assertThatThrownBy(
            () -> insertToken(
                UUID.randomUUID(),
                firstFamilyId,
                digest(9),
                createdAt.plusSeconds(60),
                expiresAt,
                createdAt.plusSeconds(3_600),
                successorId
            )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    @Test
    void shouldRejectSuccessorUsedByMultipleTokens() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        UUID successorId = UUID.randomUUID();

        Instant createdAt =
            Instant.parse(
                "2026-07-26T18:00:00Z"
            );

        Instant expiresAt =
            createdAt.plusSeconds(86_400);

        Instant consumedAt =
            createdAt.plusSeconds(3_600);

        insertUser(userId);

        insertFamily(
            familyId,
            userId,
            createdAt,
            expiresAt
        );

        insertToken(
            successorId,
            familyId,
            digest(10),
            consumedAt,
            expiresAt,
            null,
            null
        );

        insertToken(
            UUID.randomUUID(),
            familyId,
            digest(11),
            createdAt.plusSeconds(60),
            expiresAt,
            consumedAt,
            successorId
        );

        assertThatThrownBy(
            () -> insertToken(
                UUID.randomUUID(),
                familyId,
                digest(12),
                createdAt.plusSeconds(120),
                expiresAt,
                consumedAt,
                successorId
            )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    @Test
    void shouldRejectTokenExpirationAfterFamilyExpiration() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();

        Instant createdAt =
            Instant.parse(
                "2026-07-26T18:00:00Z"
            );

        Instant familyExpiresAt =
            createdAt.plusSeconds(86_400);

        insertUser(userId);

        insertFamily(
            familyId,
            userId,
            createdAt,
            familyExpiresAt
        );

        assertThatThrownBy(
            () -> insertToken(
                UUID.randomUUID(),
                familyId,
                digest(13),
                createdAt.plusSeconds(60),
                familyExpiresAt.plusSeconds(1),
                null,
                null
            )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    @Test
    void shouldRejectFamilyExpirationBeforeExistingTokenExpiration() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();

        Instant createdAt =
            Instant.parse(
                "2026-07-26T18:00:00Z"
            );

        Instant familyExpiresAt =
            createdAt.plusSeconds(86_400);

        insertUser(userId);

        insertFamily(
            familyId,
            userId,
            createdAt,
            familyExpiresAt
        );

        insertToken(
            UUID.randomUUID(),
            familyId,
            digest(14),
            createdAt.plusSeconds(60),
            familyExpiresAt,
            null,
            null
        );

        assertThatThrownBy(
            () -> jdbcTemplate.update(
                """
                UPDATE refresh_token_families
                SET expires_at = ?
                WHERE id = ?
                """,
                timestamp(
                    familyExpiresAt.minusSeconds(1)
                ),
                familyId
            )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    @Test
    void shouldExposeOnlyDigestBasedCredentialColumns() {
        assertThat(
            columnsOf("refresh_token_families")
        )
            .containsExactly(
                "id",
                "user_id",
                "created_at",
                "expires_at",
                "revoked_at",
                "revocation_reason"
            );

        assertThat(
            columnsOf("refresh_token_records")
        )
            .containsExactly(
                "id",
                "family_id",
                "token_digest",
                "issued_at",
                "expires_at",
                "consumed_at",
                "successor_id"
            );
    }

    private void insertUser(UUID userId) {
        Instant now =
            Instant.parse(
                "2026-07-26T18:00:00Z"
            );

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
            timestamp(now),
            timestamp(now)
        );
    }

    private void insertFamily(
        UUID familyId,
        UUID userId,
        Instant createdAt,
        Instant expiresAt
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO refresh_token_families (
                id,
                user_id,
                created_at,
                expires_at,
                revoked_at,
                revocation_reason
            )
            VALUES (?, ?, ?, ?, NULL, NULL)
            """,
            familyId,
            userId,
            timestamp(createdAt),
            timestamp(expiresAt)
        );
    }

    private void insertToken(
        UUID tokenId,
        UUID familyId,
        byte[] tokenDigest,
        Instant issuedAt,
        Instant expiresAt,
        Instant consumedAt,
        UUID successorId
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO refresh_token_records (
                id,
                family_id,
                token_digest,
                issued_at,
                expires_at,
                consumed_at,
                successor_id
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            tokenId,
            familyId,
            tokenDigest,
            timestamp(issuedAt),
            timestamp(expiresAt),
            timestamp(consumedAt),
            successorId
        );
    }

    private static Timestamp timestamp(
        Instant value
    ) {
        return value == null
            ? null
            : Timestamp.from(value);
    }

    private List<String> columnsOf(
        String tableName
    ) {
        return jdbcTemplate.queryForList(
            """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = ?
            ORDER BY ordinal_position
            """,
            String.class,
            tableName
        );
    }

    private static byte[] digest(int marker) {
        byte[] digest = new byte[32];

        Arrays.fill(
            digest,
            (byte) marker
        );

        return digest;
    }
}

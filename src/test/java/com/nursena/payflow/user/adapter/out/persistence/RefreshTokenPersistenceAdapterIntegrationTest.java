package com.nursena.payflow.user.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.user.application.port.out.RefreshTokenFamilyRepositoryPort;
import com.nursena.payflow.user.application.port.out.RefreshTokenRecordRepositoryPort;
import com.nursena.payflow.user.domain.model.RefreshTokenDigest;
import com.nursena.payflow.user.domain.model.RefreshTokenFamily;
import com.nursena.payflow.user.domain.model.RefreshTokenFamilyId;
import com.nursena.payflow.user.domain.model.RefreshTokenFamilyRevocationReason;
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
class RefreshTokenPersistenceAdapterIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager
        transactionManager;

    private TransactionTemplate
        transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate =
            new TransactionTemplate(
                transactionManager
            );

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
    void shouldRoundTripActiveFamilyAndRecord() {
        UUID userId =
            insertUser();

        Instant createdAt =
            Instant.parse(
                "2026-07-26T18:00:00Z"
            );

        Instant familyExpiresAt =
            createdAt.plusSeconds(
                86_400
            );

        RefreshTokenFamily family =
            RefreshTokenFamily.create(
                RefreshTokenFamilyId.of(
                    UUID.randomUUID()
                ),
                userId,
                createdAt,
                familyExpiresAt
            );

        RefreshTokenFamily savedFamily =
            familyRepository.save(family);

        assertThat(savedFamily.id())
            .isEqualTo(family.id());

        assertThat(savedFamily.userId())
            .isEqualTo(userId);

        assertThat(savedFamily.createdAt())
            .isEqualTo(createdAt);

        assertThat(savedFamily.expiresAt())
            .isEqualTo(familyExpiresAt);

        assertThat(savedFamily.isRevoked())
            .isFalse();

        RefreshTokenDigest digest =
            digest(1);

        RefreshTokenRecord record =
            RefreshTokenRecord.issue(
                RefreshTokenRecordId.of(
                    UUID.randomUUID()
                ),
                savedFamily,
                digest,
                createdAt.plusSeconds(60),
                familyExpiresAt
            );

        RefreshTokenRecord savedRecord =
            recordRepository.save(record);

        Optional<RefreshTokenRecord> reloaded =
            recordRepository.findById(
                savedRecord.id()
            );

        assertThat(reloaded)
            .isPresent();

        RefreshTokenRecord persistedRecord =
            reloaded.orElseThrow();

        assertThat(persistedRecord.id())
            .isEqualTo(record.id());

        assertThat(persistedRecord.familyId())
            .isEqualTo(family.id());

        assertThat(persistedRecord.digest())
            .isEqualTo(digest);

        assertThat(persistedRecord.issuedAt())
            .isEqualTo(
                createdAt.plusSeconds(60)
            );

        assertThat(persistedRecord.expiresAt())
            .isEqualTo(familyExpiresAt);

        assertThat(persistedRecord.isConsumed())
            .isFalse();

        assertThat(persistedRecord.successorId())
            .isNull();
    }

    @Test
    void shouldRoundTripRevokedFamily() {
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

        Instant revokedAt =
            createdAt.plusSeconds(
                3_600
            );

        RefreshTokenFamily revokedFamily =
            RefreshTokenFamily.create(
                RefreshTokenFamilyId.of(
                    UUID.randomUUID()
                ),
                userId,
                createdAt,
                expiresAt
            ).revoke(
                RefreshTokenFamilyRevocationReason
                    .CURRENT_SESSION_LOGOUT,
                revokedAt
            );

        familyRepository.save(
            revokedFamily
        );

        RefreshTokenFamily reloaded =
            findFamilyForUpdate(
                revokedFamily.id()
            ).orElseThrow();

        assertThat(reloaded.isRevoked())
            .isTrue();

        assertThat(reloaded.revokedAt())
            .isEqualTo(revokedAt);

        assertThat(reloaded.revocationReason())
            .isEqualTo(
                RefreshTokenFamilyRevocationReason
                    .CURRENT_SESSION_LOGOUT
            );
    }

    @Test
    void shouldRoundTripConsumedRecordLineage() {
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

        Instant consumedAt =
            createdAt.plusSeconds(
                3_600
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

        RefreshTokenRecord successor =
            RefreshTokenRecord.issue(
                RefreshTokenRecordId.of(
                    UUID.randomUUID()
                ),
                family,
                digest(2),
                consumedAt,
                expiresAt
            );

        recordRepository.save(successor);

        RefreshTokenRecord predecessor =
            RefreshTokenRecord.issue(
                RefreshTokenRecordId.of(
                    UUID.randomUUID()
                ),
                family,
                digest(3),
                createdAt.plusSeconds(60),
                expiresAt
            ).consume(
                successor.id(),
                consumedAt,
                family
            );

        recordRepository.save(
            predecessor
        );

        RefreshTokenRecord reloaded =
            recordRepository.findById(
                predecessor.id()
            ).orElseThrow();

        assertThat(reloaded.isConsumed())
            .isTrue();

        assertThat(reloaded.consumedAt())
            .isEqualTo(consumedAt);

        assertThat(reloaded.successorId())
            .isEqualTo(successor.id());
    }

    @Test
    void shouldPreserveDigestDefensiveCopiesAcrossPersistenceBoundary() {
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

        byte[] sourceDigest =
            digestBytes(4);

        RefreshTokenDigest digest =
            RefreshTokenDigest.of(
                sourceDigest
            );

        RefreshTokenRecord saved =
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

        Arrays.fill(
            sourceDigest,
            (byte) 99
        );

        byte[] exposedDigest =
            saved.digest().value();

        Arrays.fill(
            exposedDigest,
            (byte) 88
        );

        RefreshTokenRecord reloaded =
            recordRepository.findById(
                saved.id()
            ).orElseThrow();

        assertThat(
            reloaded.digest().value()
        )
            .containsOnly((byte) 4);
    }

    @Test
    void shouldRevokeOnlyActiveFamiliesForUser() {
        UUID targetUserId =
            insertUser();

        UUID otherUserId =
            insertUser();

        Instant now =
            Instant.parse(
                "2026-07-26T18:00:00Z"
            );

        RefreshTokenFamily active =
            familyRepository.save(
                newFamily(
                    targetUserId,
                    now.minusSeconds(3_600),
                    now.plusSeconds(86_400)
                )
            );

        RefreshTokenFamily expired =
            familyRepository.save(
                newFamily(
                    targetUserId,
                    now.minusSeconds(86_400),
                    now.minusSeconds(1)
                )
            );

        RefreshTokenFamily future =
            familyRepository.save(
                newFamily(
                    targetUserId,
                    now.plusSeconds(60),
                    now.plusSeconds(86_400)
                )
            );

        RefreshTokenFamily alreadyRevoked =
            familyRepository.save(
                newFamily(
                    targetUserId,
                    now.minusSeconds(7_200),
                    now.plusSeconds(86_400)
                ).revoke(
                    RefreshTokenFamilyRevocationReason
                        .ADMINISTRATIVE_REVOCATION,
                    now.minusSeconds(3_600)
                )
            );

        RefreshTokenFamily otherUserActive =
            familyRepository.save(
                newFamily(
                    otherUserId,
                    now.minusSeconds(3_600),
                    now.plusSeconds(86_400)
                )
            );

        Integer revokedCount =
            transactionTemplate.execute(
                status ->
                    familyRepository
                        .revokeAllActiveByUserId(
                            targetUserId,
                            now,
                            RefreshTokenFamilyRevocationReason
                                .ALL_SESSIONS_LOGOUT
                        )
            );

        assertThat(revokedCount)
            .isEqualTo(1);

        RefreshTokenFamily reloadedActive =
            findFamilyForUpdate(
                active.id()
            ).orElseThrow();

        assertThat(reloadedActive.isRevoked())
            .isTrue();

        assertThat(reloadedActive.revokedAt())
            .isEqualTo(now);

        assertThat(
            reloadedActive.revocationReason()
        )
            .isEqualTo(
                RefreshTokenFamilyRevocationReason
                    .ALL_SESSIONS_LOGOUT
            );

        assertThat(
            findFamilyForUpdate(
                expired.id()
            ).orElseThrow().isRevoked()
        )
            .isFalse();

        assertThat(
            findFamilyForUpdate(
                future.id()
            ).orElseThrow().isRevoked()
        )
            .isFalse();

        RefreshTokenFamily reloadedPreviouslyRevoked =
            findFamilyForUpdate(
                alreadyRevoked.id()
            ).orElseThrow();

        assertThat(
            reloadedPreviouslyRevoked
                .revocationReason()
        )
            .isEqualTo(
                RefreshTokenFamilyRevocationReason
                    .ADMINISTRATIVE_REVOCATION
            );

        assertThat(
            findFamilyForUpdate(
                otherUserActive.id()
            ).orElseThrow().isRevoked()
        )
            .isFalse();
    }

    @Test
    void shouldFindRecordByDigestForUpdateInsideTransaction() {
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
            digest(5);

        RefreshTokenRecord saved =
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

        Optional<RefreshTokenRecord> found =
            findRecordByDigestForUpdate(
                digest
            );

        assertThat(found)
            .isPresent();

        assertThat(found.orElseThrow().id())
            .isEqualTo(saved.id());
    }

    @Test
    void shouldReturnEmptyForUnknownIdentifiers() {
        Optional<RefreshTokenFamily>
            missingFamily =
            findFamilyForUpdate(
                RefreshTokenFamilyId.of(
                    UUID.randomUUID()
                )
            );

        Optional<RefreshTokenRecord>
            missingRecord =
            recordRepository.findById(
                RefreshTokenRecordId.of(
                    UUID.randomUUID()
                )
            );

        Optional<RefreshTokenRecord>
            missingDigest =
            findRecordByDigestForUpdate(
                digest(6)
            );

        assertThat(missingFamily)
            .isEmpty();

        assertThat(missingRecord)
            .isEmpty();

        assertThat(missingDigest)
            .isEmpty();
    }

    private Optional<RefreshTokenFamily>
    findFamilyForUpdate(
        RefreshTokenFamilyId familyId
    ) {
        Optional<RefreshTokenFamily> result =
            transactionTemplate.execute(
                status ->
                    familyRepository
                        .findByIdForUpdate(
                            familyId
                        )
            );

        return result == null
            ? Optional.empty()
            : result;
    }

    private Optional<RefreshTokenRecord>
    findRecordByDigestForUpdate(
        RefreshTokenDigest digest
    ) {
        Optional<RefreshTokenRecord> result =
            transactionTemplate.execute(
                status ->
                    recordRepository
                        .findByDigestForUpdate(
                            digest
                        )
            );

        return result == null
            ? Optional.empty()
            : result;
    }

    private static RefreshTokenFamily
    newFamily(
        UUID userId,
        Instant createdAt,
        Instant expiresAt
    ) {
        return RefreshTokenFamily.create(
            RefreshTokenFamilyId.of(
                UUID.randomUUID()
            ),
            userId,
            createdAt,
            expiresAt
        );
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

    private static RefreshTokenDigest
    digest(
        int marker
    ) {
        return RefreshTokenDigest.of(
            digestBytes(marker)
        );
    }

    private static byte[] digestBytes(
        int marker
    ) {
        byte[] digest =
            new byte[
                RefreshTokenDigest
                    .SHA_256_LENGTH_BYTES
            ];

        Arrays.fill(
            digest,
            (byte) marker
        );

        return digest;
    }
}

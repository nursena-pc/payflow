package com.nursena.payflow.user.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataMfaLoginChallengeRepository
    extends JpaRepository<MfaLoginChallengeJpaEntity, UUID> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
            UPDATE mfa_login_challenges
            SET state = 'SUPERSEDED',
                resolved_at = :resolvedAt
            WHERE user_id = :userId
              AND state = 'PENDING'
            """,
        nativeQuery = true
    )
    int supersedePendingByUserId(
        @Param("userId") UUID userId,
        @Param("resolvedAt") Instant resolvedAt
    );

    @Query("""
        SELECT challenge.userId
        FROM MfaLoginChallengeJpaEntity challenge
        WHERE challenge.challengeDigest = :digest
        """)
    Optional<UUID> findUserIdByDigest(@Param("digest") byte[] digest);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT challenge
        FROM MfaLoginChallengeJpaEntity challenge
        WHERE challenge.challengeDigest = :digest
        """)
    Optional<MfaLoginChallengeJpaEntity> findByDigestForUpdate(
        @Param("digest") byte[] digest
    );
}

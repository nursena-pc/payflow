package com.nursena.payflow.user.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.user.domain.model.RefreshTokenFamilyRevocationReason;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataRefreshTokenFamilyRepository
    extends JpaRepository<
        RefreshTokenFamilyJpaEntity,
        UUID
    > {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT family
        FROM RefreshTokenFamilyJpaEntity family
        WHERE family.id = :familyId
        """)
    Optional<RefreshTokenFamilyJpaEntity>
    findByIdForUpdate(
        @Param("familyId")
        UUID familyId
    );

    @Modifying(
        flushAutomatically = true,
        clearAutomatically = true
    )
    @Query("""
        UPDATE RefreshTokenFamilyJpaEntity family
        SET family.revokedAt = :revokedAt,
            family.revocationReason = :reason
        WHERE family.userId = :userId
          AND family.revokedAt IS NULL
          AND family.revocationReason IS NULL
          AND family.createdAt <= :revokedAt
          AND family.expiresAt > :revokedAt
        """)
    int revokeAllActiveByUserId(
        @Param("userId")
        UUID userId,
        @Param("revokedAt")
        Instant revokedAt,
        @Param("reason")
        RefreshTokenFamilyRevocationReason reason
    );
}

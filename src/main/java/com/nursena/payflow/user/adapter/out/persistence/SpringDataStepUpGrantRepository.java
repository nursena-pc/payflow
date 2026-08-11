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

interface SpringDataStepUpGrantRepository
    extends JpaRepository<StepUpGrantJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT grant
        FROM StepUpGrantJpaEntity grant
        WHERE grant.grantDigest = :digest
        """)
    Optional<StepUpGrantJpaEntity> findByDigestForUpdate(
        @Param("digest") byte[] digest
    );

    @Modifying(flushAutomatically = true)
    @Query("""
        UPDATE StepUpGrantJpaEntity grant
        SET grant.supersededAt = :supersededAt
        WHERE grant.subjectId = :subjectId
          AND grant.purpose = :purpose
          AND grant.consumedAt IS NULL
          AND grant.supersededAt IS NULL
        """)
    int supersedeUnconsumedBySubjectAndPurpose(
        @Param("subjectId") UUID subjectId,
        @Param("purpose") String purpose,
        @Param("supersededAt") Instant supersededAt
    );
}

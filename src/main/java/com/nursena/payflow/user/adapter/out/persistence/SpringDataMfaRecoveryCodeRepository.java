package com.nursena.payflow.user.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataMfaRecoveryCodeRepository
    extends JpaRepository<MfaRecoveryCodeJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT recoveryCode
        FROM MfaRecoveryCodeJpaEntity recoveryCode
        WHERE recoveryCode.userId = :userId
          AND recoveryCode.codeDigest = :digest
        """)
    Optional<MfaRecoveryCodeJpaEntity> findByUserIdAndDigestForUpdate(
        @Param("userId") UUID userId,
        @Param("digest") byte[] digest
    );

    @Modifying
    @Query("""
        DELETE FROM MfaRecoveryCodeJpaEntity recoveryCode
        WHERE recoveryCode.userId = :userId
        """)
    void deleteAllByUserId(@Param("userId") UUID userId);
}

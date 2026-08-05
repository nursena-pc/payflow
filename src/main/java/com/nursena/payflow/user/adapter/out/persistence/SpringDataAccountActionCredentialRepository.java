package com.nursena.payflow.user.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.user.domain.model
    .AccountActionCredentialPurpose;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataAccountActionCredentialRepository
    extends JpaRepository<
        AccountActionCredentialJpaEntity,
        UUID
    > {

    @Modifying(
        flushAutomatically = true,
        clearAutomatically = true
    )
    @Query("""
        UPDATE AccountActionCredentialJpaEntity credential
        SET credential.supersededAt = :supersededAt
        WHERE credential.userId = :userId
          AND credential.purpose = :purpose
          AND credential.consumedAt IS NULL
          AND credential.supersededAt IS NULL
        """)
    int supersedeUnresolved(
        @Param("userId") UUID userId,
        @Param("purpose")
        AccountActionCredentialPurpose purpose,
        @Param("supersededAt") Instant supersededAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT credential
        FROM AccountActionCredentialJpaEntity credential
        WHERE credential.credentialDigest = :digest
          AND credential.purpose = :purpose
        """)
    Optional<AccountActionCredentialJpaEntity>
    findByDigestAndPurposeForUpdate(
        @Param("digest") byte[] digest,
        @Param("purpose")
        AccountActionCredentialPurpose purpose
    );
}

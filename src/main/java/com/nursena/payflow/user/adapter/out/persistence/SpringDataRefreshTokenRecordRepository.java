package com.nursena.payflow.user.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataRefreshTokenRecordRepository
    extends JpaRepository<
        RefreshTokenRecordJpaEntity,
        UUID
    > {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT record
        FROM RefreshTokenRecordJpaEntity record
        WHERE record.tokenDigest = :digest
        """)
    Optional<RefreshTokenRecordJpaEntity>
    findByDigestForUpdate(
        @Param("digest")
        byte[] digest
    );
}

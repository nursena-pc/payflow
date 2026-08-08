package com.nursena.payflow.user.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataMfaAuthenticatorRepository
    extends JpaRepository<MfaAuthenticatorJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "select authenticator from MfaAuthenticatorJpaEntity authenticator "
            + "where authenticator.userId = :userId"
    )
    Optional<MfaAuthenticatorJpaEntity> findByUserIdForUpdate(
        @Param("userId") UUID userId
    );
}

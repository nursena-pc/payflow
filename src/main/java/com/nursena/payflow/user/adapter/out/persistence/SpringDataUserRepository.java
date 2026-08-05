package com.nursena.payflow.user.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataUserRepository
    extends JpaRepository<UserJpaEntity, UUID> {

    boolean existsByEmail(String email);

    Optional<UserJpaEntity> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT user
        FROM UserJpaEntity user
        WHERE user.email = :email
        """)
    Optional<UserJpaEntity> findByEmailForUpdate(
        @Param("email") String email
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT user
        FROM UserJpaEntity user
        WHERE user.id = :userId
        """)
    Optional<UserJpaEntity> findByIdForUpdate(
        @Param("userId") UUID userId
    );
}

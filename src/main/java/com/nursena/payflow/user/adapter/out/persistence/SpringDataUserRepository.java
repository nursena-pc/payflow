package com.nursena.payflow.user.adapter.out.persistence;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataUserRepository
    extends JpaRepository<UserJpaEntity, UUID> {

    boolean existsByEmail(String email);
    Optional<UserJpaEntity> findByEmail(String email);
}

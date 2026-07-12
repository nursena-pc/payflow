package com.nursena.payflow.wallet.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataWalletRepository extends JpaRepository<WalletJpaEntity, UUID> {
    boolean existsByOwnerId(UUID ownerId);
}

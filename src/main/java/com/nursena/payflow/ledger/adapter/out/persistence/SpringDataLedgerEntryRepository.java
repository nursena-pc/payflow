package com.nursena.payflow.ledger.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataLedgerEntryRepository
    extends JpaRepository<LedgerEntryJpaEntity, UUID> {
}

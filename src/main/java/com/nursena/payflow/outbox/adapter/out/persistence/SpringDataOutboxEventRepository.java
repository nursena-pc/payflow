package com.nursena.payflow.outbox.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataOutboxEventRepository
    extends JpaRepository<
    OutboxEventJpaEntity,
    UUID
    > {
}

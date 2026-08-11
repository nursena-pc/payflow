package com.nursena.payflow.user.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataAccountSecurityAuditRepository
    extends JpaRepository<AccountSecurityAuditJpaEntity, UUID> {
}

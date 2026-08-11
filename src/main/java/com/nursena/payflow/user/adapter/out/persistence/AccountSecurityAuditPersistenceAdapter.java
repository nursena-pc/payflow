package com.nursena.payflow.user.adapter.out.persistence;

import java.util.Objects;

import com.nursena.payflow.user.application.port.out.AccountSecurityAuditPort;
import com.nursena.payflow.user.domain.model.AccountSecurityAuditEvent;
import org.springframework.stereotype.Component;

@Component
class AccountSecurityAuditPersistenceAdapter
    implements AccountSecurityAuditPort {

    private final SpringDataAccountSecurityAuditRepository
        repository;

    AccountSecurityAuditPersistenceAdapter(
        SpringDataAccountSecurityAuditRepository repository
    ) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public void append(AccountSecurityAuditEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        repository.saveAndFlush(
            new AccountSecurityAuditJpaEntity(
                event.id(),
                event.subjectUserId(),
                event.action(),
                event.occurredAt()
            )
        );
    }
}

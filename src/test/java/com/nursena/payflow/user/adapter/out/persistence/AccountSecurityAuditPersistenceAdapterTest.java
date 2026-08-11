package com.nursena.payflow.user.adapter.out.persistence;

import static org.assertj.core.api.Assertions
    .assertThat;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.user.domain.model
    .AccountSecurityAuditAction;
import com.nursena.payflow.user.domain.model
    .AccountSecurityAuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountSecurityAuditPersistenceAdapterTest {

    private static final UUID AUDIT_ID =
        UUID.fromString(
            "48915435-5ebc-48fb-8ef1-c0968bab9fa3"
        );

    private static final UUID SUBJECT_USER_ID =
        UUID.fromString(
            "0d147a7c-7232-4145-902f-b4655cc903ba"
        );

    private static final Instant OCCURRED_AT =
        Instant.parse("2026-08-11T12:00:00Z");

    @Mock
    private SpringDataAccountSecurityAuditRepository
        repository;

    private AccountSecurityAuditPersistenceAdapter
        adapter;

    @BeforeEach
    void setUp() {
        adapter =
            new AccountSecurityAuditPersistenceAdapter(
                repository
            );
    }

    @Test
    void shouldAppendAuditEvent() {
        AccountSecurityAuditEvent event =
            new AccountSecurityAuditEvent(
                AUDIT_ID,
                SUBJECT_USER_ID,
                AccountSecurityAuditAction.MFA_DISABLED,
                OCCURRED_AT
            );

        adapter.append(event);

        ArgumentCaptor<AccountSecurityAuditJpaEntity>
            entityCaptor =
                ArgumentCaptor.forClass(
                    AccountSecurityAuditJpaEntity.class
                );

        verify(repository).saveAndFlush(
            entityCaptor.capture()
        );

        AccountSecurityAuditJpaEntity persisted =
            entityCaptor.getValue();

        assertThat(persisted.getId())
            .isEqualTo(AUDIT_ID);
        assertThat(persisted.getSubjectUserId())
            .isEqualTo(SUBJECT_USER_ID);
        assertThat(persisted.getAction())
            .isEqualTo(
                AccountSecurityAuditAction.MFA_DISABLED
            );
        assertThat(persisted.getOccurredAt())
            .isEqualTo(OCCURRED_AT);
    }
}

package com.nursena.payflow.user.domain.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountSecurityAuditEventTest {

    @Test
    void recoveryCodesRotatedCreatesExpectedAuditEvent() {
        UUID id = UUID.randomUUID();
        UUID subjectUserId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-08-11T10:15:30Z");

        AccountSecurityAuditEvent event =
            AccountSecurityAuditEvent.recoveryCodesRotated(
                id,
                subjectUserId,
                occurredAt
            );

        assertAll(
            () -> assertEquals(id, event.id()),
            () -> assertEquals(subjectUserId, event.subjectUserId()),
            () -> assertEquals(
                AccountSecurityAuditAction.RECOVERY_CODES_ROTATED,
                event.action()
            ),
            () -> assertEquals(occurredAt, event.occurredAt())
        );
    }
}
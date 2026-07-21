package com.nursena.payflow.eventprocessing.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.application.port.out.TransferCompletedEventHandlerPort;
import com.nursena.payflow.transaction.application.model.TransferCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class TransferCompletedEventAuditPersistenceIntegrationTest {

    private static final UUID EVENT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000000901"
        );

    private static final UUID TRANSACTION_ID =
        UUID.fromString(
            "60000000-0000-0000-0000-000000000901"
        );

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
            "postgres:17-alpine"
        );

    @Autowired
    private TransferCompletedEventHandlerPort
        eventHandler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update(
            "DELETE FROM transfer_completed_event_audits"
        );
    }

    @Test
    void shouldPersistTransferCompletedAudit() {
        eventHandler.handle(
            event(
                EVENT_ID,
                TRANSACTION_ID
            )
        );

        Map<String, Object> stored =
            jdbcTemplate.queryForMap(
                """
                SELECT
                    event_type,
                    event_version,
                    occurred_at,
                    transaction_id,
                    source_wallet_id,
                    target_wallet_id,
                    amount,
                    currency,
                    recorded_at
                FROM transfer_completed_event_audits
                WHERE event_id = ?
                """,
                EVENT_ID
            );

        assertThat(
            stored.get("event_type")
        )
            .isEqualTo(
                TransferCompletedEvent.TYPE
            );

        assertThat(
            stored.get("event_version")
        )
            .isEqualTo(
                TransferCompletedEvent.VERSION
            );

        assertThat(
            stored.get("transaction_id")
        )
            .isEqualTo(TRANSACTION_ID);

        assertThat(
            (BigDecimal) stored.get("amount")
        )
            .isEqualByComparingTo(
                "125.50"
            );

        assertThat(
            stored.get("currency")
        )
            .isEqualTo("TRY");

        assertThat(
            stored.get("recorded_at")
        )
            .isInstanceOf(
                Timestamp.class
            );
    }

    @Test
    void shouldRejectSecondAuditForSameTransaction() {
        eventHandler.handle(
            event(
                EVENT_ID,
                TRANSACTION_ID
            )
        );

        assertThatThrownBy(
            () -> eventHandler.handle(
                event(
                    UUID.fromString(
                        "50000000-0000-0000-0000-000000000902"
                    ),
                    TRANSACTION_ID
                )
            )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    private static TransferCompletedEvent event(
        UUID eventId,
        UUID transactionId
    ) {
        return new TransferCompletedEvent(
            eventId,
            TransferCompletedEvent.TYPE,
            TransferCompletedEvent.VERSION,
            Instant.parse(
                "2026-07-20T20:00:00Z"
            ),
            transactionId,
            UUID.fromString(
                "70000000-0000-0000-0000-000000000901"
            ),
            UUID.fromString(
                "70000000-0000-0000-0000-000000000902"
            ),
            "125.50",
            "TRY"
        );
    }
}

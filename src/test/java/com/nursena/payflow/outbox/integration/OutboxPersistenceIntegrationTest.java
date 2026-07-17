package com.nursena.payflow.outbox.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.outbox.application.port.out.OutboxEventRepositoryPort;
import com.nursena.payflow.outbox.domain.exception.DuplicateOutboxEventException;
import com.nursena.payflow.outbox.domain.model.OutboxEvent;
import com.nursena.payflow.outbox.domain.model.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class OutboxPersistenceIntegrationTest {

    private static final UUID EVENT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000000001"
        );

    private static final UUID SECOND_EVENT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000000002"
        );

    private static final UUID TRANSACTION_ID =
        UUID.fromString(
            "60000000-0000-0000-0000-000000000001"
        );

    private static final Instant CREATED_AT =
        Instant.parse(
            "2026-07-17T19:00:00Z"
        );

    private static final String EVENT_TYPE =
        "wallet.transfer.completed";

    private static final String DEDUPLICATION_KEY =
        EVENT_TYPE + ":1:" + TRANSACTION_ID;

    private static final String PAYLOAD = """
        {
          "eventId": "50000000-0000-0000-0000-000000000001",
          "eventType": "wallet.transfer.completed",
          "eventVersion": 1,
          "occurredAt": "2026-07-17T19:00:00Z",
          "transactionId": "60000000-0000-0000-0000-000000000001",
          "amount": "125.50",
          "currency": "TRY"
        }
        """;

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
            "postgres:17-alpine"
        );

    @Autowired
    private OutboxEventRepositoryPort repositoryPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update(
            "DELETE FROM outbox_events"
        );
    }

    @Test
    void shouldPersistAndRestorePendingEvent() {
        OutboxEvent saved =
            repositoryPort.save(
                pendingEvent(
                    EVENT_ID,
                    DEDUPLICATION_KEY
                )
            );

        Optional<OutboxEvent> result =
            repositoryPort.findById(
                EVENT_ID
            );

        assertThat(result)
            .isPresent();

        OutboxEvent restored =
            result.orElseThrow();

        assertThat(saved.id())
            .isEqualTo(EVENT_ID);

        assertThat(restored.id())
            .isEqualTo(EVENT_ID);

        assertThat(restored.aggregateType())
            .isEqualTo("PAYMENT_TRANSACTION");

        assertThat(restored.aggregateId())
            .isEqualTo(TRANSACTION_ID);

        assertThat(restored.eventType())
            .isEqualTo(EVENT_TYPE);

        assertThat(restored.eventVersion())
            .isEqualTo(1);

        assertThat(restored.topic())
            .isEqualTo(EVENT_TYPE);

        assertThat(restored.partitionKey())
            .isEqualTo(TRANSACTION_ID.toString());

        assertThat(restored.deduplicationKey())
            .isEqualTo(DEDUPLICATION_KEY);

        assertThat(restored.status())
            .isEqualTo(OutboxStatus.PENDING);

        assertThat(restored.attemptCount())
            .isZero();

        assertThat(restored.availableAt())
            .isEqualTo(CREATED_AT);

        assertThat(restored.createdAt())
            .isEqualTo(CREATED_AT);

        assertThat(restored.lockedAt())
            .isNull();

        assertThat(restored.lockedUntil())
            .isNull();

        assertThat(restored.lockedBy())
            .isNull();

        assertThat(restored.publishedAt())
            .isNull();

        assertThat(restored.lastError())
            .isNull();
    }

    @Test
    void shouldPersistPayloadAsJsonObject() {
        repositoryPort.save(
            pendingEvent(
                EVENT_ID,
                DEDUPLICATION_KEY
            )
        );

        String payloadType =
            jdbcTemplate.queryForObject(
                """
                SELECT jsonb_typeof(payload)
                FROM outbox_events
                WHERE id = ?
                """,
                String.class,
                EVENT_ID
            );

        String transactionId =
            jdbcTemplate.queryForObject(
                """
                SELECT payload ->> 'transactionId'
                FROM outbox_events
                WHERE id = ?
                """,
                String.class,
                EVENT_ID
            );

        String amount =
            jdbcTemplate.queryForObject(
                """
                SELECT payload ->> 'amount'
                FROM outbox_events
                WHERE id = ?
                """,
                String.class,
                EVENT_ID
            );

        assertThat(payloadType)
            .isEqualTo("object");

        assertThat(transactionId)
            .isEqualTo(
                TRANSACTION_ID.toString()
            );

        assertThat(amount)
            .isEqualTo("125.50");
    }

    @Test
    void shouldTranslateDuplicateDeduplicationKey() {
        repositoryPort.save(
            pendingEvent(
                EVENT_ID,
                DEDUPLICATION_KEY
            )
        );

        OutboxEvent duplicate =
            pendingEvent(
                SECOND_EVENT_ID,
                DEDUPLICATION_KEY
            );

        assertThatThrownBy(() ->
            repositoryPort.save(duplicate)
        )
            .isInstanceOf(
                DuplicateOutboxEventException.class
            )
            .hasMessage(
                "An outbox event already exists "
                    + "for the same deduplication key."
            );
    }

    @Test
    void shouldReturnEmptyForUnknownEvent() {
        Optional<OutboxEvent> result =
            repositoryPort.findById(
                UUID.fromString(
                    "ffffffff-ffff-ffff-ffff-ffffffffffff"
                )
            );

        assertThat(result)
            .isEmpty();
    }

    private static OutboxEvent pendingEvent(
        UUID eventId,
        String deduplicationKey
    ) {
        return OutboxEvent.pending(
            eventId,
            "PAYMENT_TRANSACTION",
            TRANSACTION_ID,
            EVENT_TYPE,
            1,
            EVENT_TYPE,
            TRANSACTION_ID.toString(),
            deduplicationKey,
            PAYLOAD,
            CREATED_AT
        );
    }
}

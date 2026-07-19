package com.nursena.payflow.outbox.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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
class OutboxSchemaIntegrationTest {

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

    private static final String DEDUPLICATION_KEY =
        "wallet.transfer.completed:1:" + TRANSACTION_ID;

    private static final String PAYLOAD = """
        {
          "eventId": "50000000-0000-0000-0000-000000000001",
          "eventType": "wallet.transfer.completed",
          "eventVersion": 1,
          "occurredAt": "2026-07-17T19:00:00Z",
          "transactionId": "60000000-0000-0000-0000-000000000001",
          "sourceWalletId": "70000000-0000-0000-0000-000000000001",
          "targetWalletId": "70000000-0000-0000-0000-000000000002",
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
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update(
            "DELETE FROM outbox_events"
        );
    }

    @Test
    void shouldPersistValidPendingEvent() {
        insertPendingEvent(
            EVENT_ID,
            DEDUPLICATION_KEY
        );

        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM outbox_events
            WHERE id = ?
            """,
            Integer.class,
            EVENT_ID
        );

        String status = jdbcTemplate.queryForObject(
            """
            SELECT status
            FROM outbox_events
            WHERE id = ?
            """,
            String.class,
            EVENT_ID
        );

        String transactionId = jdbcTemplate.queryForObject(
            """
            SELECT payload ->> 'transactionId'
            FROM outbox_events
            WHERE id = ?
            """,
            String.class,
            EVENT_ID
        );

        assertThat(count)
            .isEqualTo(1);

        assertThat(status)
            .isEqualTo("PENDING");

        assertThat(transactionId)
            .isEqualTo(
                TRANSACTION_ID.toString()
            );
    }

    @Test
    void shouldRejectDuplicateDeduplicationKey() {
        insertPendingEvent(
            EVENT_ID,
            DEDUPLICATION_KEY
        );

        assertConstraintViolation(
            () -> insertPendingEvent(
                SECOND_EVENT_ID,
                DEDUPLICATION_KEY
            ),
            "uq_outbox_events_deduplication_key"
        );
    }

    @Test
    void shouldRejectInvalidEventVersion() {
        insertPendingEvent(
            EVENT_ID,
            DEDUPLICATION_KEY
        );

        assertConstraintViolation(
            () -> jdbcTemplate.update(
                """
                UPDATE outbox_events
                SET event_version = 0
                WHERE id = ?
                """,
                EVENT_ID
            ),
            "chk_outbox_events_event_version"
        );
    }

    @Test
    void shouldRejectNegativeAttemptCount() {
        insertPendingEvent(
            EVENT_ID,
            DEDUPLICATION_KEY
        );

        assertConstraintViolation(
            () -> jdbcTemplate.update(
                """
                UPDATE outbox_events
                SET attempt_count = -1
                WHERE id = ?
                """,
                EVENT_ID
            ),
            "chk_outbox_events_attempt_count"
        );
    }

    @Test
    void shouldRejectUnsupportedStatus() {
        insertPendingEvent(
            EVENT_ID,
            DEDUPLICATION_KEY
        );

        assertConstraintViolation(
            () -> jdbcTemplate.update(
                """
                UPDATE outbox_events
                SET status = 'UNKNOWN'
                WHERE id = ?
                """,
                EVENT_ID
            ),
            "chk_outbox_events_status"
        );
    }

    @Test
    void shouldRejectEmptyPayload() {
        insertPendingEvent(
            EVENT_ID,
            DEDUPLICATION_KEY
        );

        assertConstraintViolation(
            () -> jdbcTemplate.update(
                """
                UPDATE outbox_events
                SET payload = '{}'::jsonb
                WHERE id = ?
                """,
                EVENT_ID
            ),
            "chk_outbox_events_payload"
        );
    }

    @Test
    void shouldRejectAvailabilityBeforeCreation() {
        insertPendingEvent(
            EVENT_ID,
            DEDUPLICATION_KEY
        );

        assertConstraintViolation(
            () -> jdbcTemplate.update(
                """
                UPDATE outbox_events
                SET available_at = ?
                WHERE id = ?
                """,
                Timestamp.from(
                    CREATED_AT.minusSeconds(1)
                ),
                EVENT_ID
            ),
            "chk_outbox_events_availability"
        );
    }

    @Test
    void shouldRequireLeaseForProcessingEvent() {
        insertPendingEvent(
            EVENT_ID,
            DEDUPLICATION_KEY
        );

        assertConstraintViolation(
            () -> jdbcTemplate.update(
                """
                UPDATE outbox_events
                SET status = 'PROCESSING'
                WHERE id = ?
                """,
                EVENT_ID
            ),
            "chk_outbox_events_processing_lock"
        );
    }

    @Test
    void shouldRequirePublishedAtForPublishedEvent() {
        insertPendingEvent(
            EVENT_ID,
            DEDUPLICATION_KEY
        );

        assertConstraintViolation(
            () -> jdbcTemplate.update(
                """
                UPDATE outbox_events
                SET status = 'PUBLISHED'
                WHERE id = ?
                """,
                EVENT_ID
            ),
            "chk_outbox_events_publication"
        );
    }

    private void insertPendingEvent(
        UUID eventId,
        String deduplicationKey
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO outbox_events (
                id,
                aggregate_type,
                aggregate_id,
                event_type,
                event_version,
                topic,
                partition_key,
                deduplication_key,
                payload,
                status,
                attempt_count,
                available_at,
                locked_at,
                locked_until,
                locked_by,
                created_at,
                published_at,
                last_error
            )
            VALUES (
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                CAST(? AS JSONB),
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?
            )
            """,
            eventId,
            "PAYMENT_TRANSACTION",
            TRANSACTION_ID,
            "wallet.transfer.completed",
            1,
            "wallet.transfer.completed",
            TRANSACTION_ID.toString(),
            deduplicationKey,
            PAYLOAD,
            "PENDING",
            0,
            Timestamp.from(CREATED_AT),
            null,
            null,
            null,
            Timestamp.from(CREATED_AT),
            null,
            null
        );
    }

    private static void assertConstraintViolation(
        ThrowingCallable operation,
        String constraintName
    ) {
        Throwable thrown = catchThrowable(
            operation
        );

        assertThat(thrown)
            .isInstanceOf(
                DataIntegrityViolationException.class
            );

        assertThat(rootCause(thrown).getMessage())
            .contains(constraintName);
    }

    private static Throwable rootCause(
        Throwable throwable
    ) {
        Throwable current = throwable;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        return current;
    }
}

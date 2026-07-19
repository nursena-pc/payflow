package com.nursena.payflow.outbox.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.outbox.application.model.OutboxBacklogSnapshot;
import com.nursena.payflow.outbox.application.port.out.OutboxBacklogQueryPort;
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
class OutboxBacklogQueryIntegrationTest {

    private static final Instant ACTIVE_OLDEST =
        Instant.parse(
            "2026-07-19T10:05:00Z"
        );

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
            "postgres:17-alpine"
        );

    @Autowired
    private OutboxBacklogQueryPort queryPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update(
            "DELETE FROM outbox_events"
        );
    }

    @Test
    void shouldReturnEmptySnapshotWhenNoEventsExist() {
        OutboxBacklogSnapshot snapshot =
            queryPort.loadSnapshot();

        assertThat(snapshot.eventCount())
            .isZero();

        assertThat(snapshot.oldestCreatedAt())
            .isEmpty();
    }

    @Test
    void shouldCountOnlyPendingAndProcessingEvents() {
        insertEvent(
            "50000000-0000-0000-0000-000000000201",
            OutboxStatus.PUBLISHED,
            Instant.parse(
                "2026-07-19T10:00:00Z"
            )
        );

        insertEvent(
            "50000000-0000-0000-0000-000000000202",
            OutboxStatus.FAILED,
            Instant.parse(
                "2026-07-19T10:01:00Z"
            )
        );

        insertEvent(
            "50000000-0000-0000-0000-000000000203",
            OutboxStatus.PENDING,
            ACTIVE_OLDEST
        );

        insertEvent(
            "50000000-0000-0000-0000-000000000204",
            OutboxStatus.PROCESSING,
            Instant.parse(
                "2026-07-19T10:10:00Z"
            )
        );

        OutboxBacklogSnapshot snapshot =
            queryPort.loadSnapshot();

        assertThat(snapshot.eventCount())
            .isEqualTo(2);

        assertThat(snapshot.oldestCreatedAt())
            .contains(ACTIVE_OLDEST);
    }

    private void insertEvent(
        String idValue,
        OutboxStatus status,
        Instant createdAt
    ) {
        UUID id =
            UUID.fromString(idValue);

        boolean processing =
            status == OutboxStatus.PROCESSING;

        boolean published =
            status == OutboxStatus.PUBLISHED;

        int attemptCount =
            status == OutboxStatus.PENDING
                ? 0
                : 1;

        Instant lockedAt =
            processing
                ? createdAt.plusSeconds(1)
                : null;

        Instant lockedUntil =
            processing
                ? createdAt.plusSeconds(31)
                : null;

        String lockedBy =
            processing
                ? "backlog-test-publisher"
                : null;

        Instant publishedAt =
            published
                ? createdAt.plusSeconds(5)
                : null;

        String lastError =
            status == OutboxStatus.FAILED
                ? "Terminal test failure."
                : null;

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
                'PAYMENT_TRANSACTION',
                ?,
                'wallet.transfer.completed',
                1,
                'wallet.transfer.completed',
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
            id,
            id,
            id.toString(),
            "wallet.transfer.completed:1:" + id,
            "{\"eventId\":\"" + id + "\"}",
            status.name(),
            attemptCount,
            timestamp(createdAt),
            timestamp(lockedAt),
            timestamp(lockedUntil),
            lockedBy,
            timestamp(createdAt),
            timestamp(publishedAt),
            lastError
        );
    }

    private static Timestamp timestamp(
        Instant value
    ) {
        return value == null
            ? null
            : Timestamp.from(value);
    }
}

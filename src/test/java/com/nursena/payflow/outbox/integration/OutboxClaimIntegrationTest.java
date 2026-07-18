package com.nursena.payflow.outbox.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.nursena.payflow.outbox.application.port.out.OutboxEventClaimPort;
import com.nursena.payflow.outbox.domain.model.OutboxEvent;
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
class OutboxClaimIntegrationTest {

    private static final UUID EXPIRED_EVENT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000000001"
        );

    private static final UUID EARLY_PENDING_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000000002"
        );

    private static final UUID TIE_FIRST_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000000003"
        );

    private static final UUID TIE_SECOND_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000000004"
        );

    private static final UUID FUTURE_EVENT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000000005"
        );

    private static final UUID ACTIVE_EVENT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000000006"
        );

    private static final UUID AGGREGATE_ID =
        UUID.fromString(
            "60000000-0000-0000-0000-000000000001"
        );

    private static final String EVENT_TYPE =
        "wallet.transfer.completed";

    private static final Instant CREATED_AT =
        Instant.parse(
            "2026-07-18T10:00:00Z"
        );

    private static final Instant CLAIMED_AT =
        Instant.parse(
            "2026-07-18T10:10:00Z"
        );

    private static final Duration LEASE_DURATION =
        Duration.ofSeconds(30);

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
            "postgres:17-alpine"
        );

    @Autowired
    private OutboxEventClaimPort claimPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpDatabase() {
        jdbcTemplate.update(
            "DELETE FROM outbox_events"
        );

        insertEvent(
            EXPIRED_EVENT_ID,
            "PROCESSING",
            1,
            CREATED_AT,
            CLAIMED_AT.minusSeconds(60),
            CLAIMED_AT.minusSeconds(20),
            "expired-publisher",
            "Previous publishing failure."
        );

        insertEvent(
            EARLY_PENDING_ID,
            "PENDING",
            0,
            CLAIMED_AT.minusSeconds(10),
            null,
            null,
            null,
            null
        );

        insertEvent(
            TIE_FIRST_ID,
            "PENDING",
            0,
            CLAIMED_AT,
            null,
            null,
            null,
            null
        );

        insertEvent(
            TIE_SECOND_ID,
            "PENDING",
            0,
            CLAIMED_AT,
            null,
            null,
            null,
            null
        );

        insertEvent(
            FUTURE_EVENT_ID,
            "PENDING",
            0,
            CLAIMED_AT.plusSeconds(60),
            null,
            null,
            null,
            null
        );

        insertEvent(
            ACTIVE_EVENT_ID,
            "PROCESSING",
            1,
            CREATED_AT,
            CLAIMED_AT.minusSeconds(5),
            CLAIMED_AT.plusSeconds(60),
            "active-publisher",
            null
        );
    }

    @Test
    void shouldClaimAvailableEventsInDeterministicBatches() {
        List<OutboxEvent> firstBatch =
            claimPort.claimAvailable(
                "publisher-1",
                CLAIMED_AT,
                LEASE_DURATION,
                3
            );

        assertThat(firstBatch)
            .extracting(OutboxEvent::id)
            .containsExactly(
                EXPIRED_EVENT_ID,
                EARLY_PENDING_ID,
                TIE_FIRST_ID
            );

        assertClaimedState(
            EXPIRED_EVENT_ID,
            "publisher-1",
            2
        );

        assertClaimedState(
            EARLY_PENDING_ID,
            "publisher-1",
            1
        );

        assertClaimedState(
            TIE_FIRST_ID,
            "publisher-1",
            1
        );

        assertThat(
            stateOf(EXPIRED_EVENT_ID).lastError()
        ).isEqualTo(
            "Previous publishing failure."
        );

        List<OutboxEvent> secondBatch =
            claimPort.claimAvailable(
                "publisher-2",
                CLAIMED_AT,
                LEASE_DURATION,
                10
            );

        assertThat(secondBatch)
            .extracting(OutboxEvent::id)
            .containsExactly(TIE_SECOND_ID);

        assertClaimedState(
            TIE_SECOND_ID,
            "publisher-2",
            1
        );

        assertThat(
            stateOf(FUTURE_EVENT_ID).status()
        ).isEqualTo("PENDING");

        EventState activeState =
            stateOf(ACTIVE_EVENT_ID);

        assertThat(activeState.status())
            .isEqualTo("PROCESSING");

        assertThat(activeState.lockedBy())
            .isEqualTo("active-publisher");

        List<OutboxEvent> thirdBatch =
            claimPort.claimAvailable(
                "publisher-3",
                CLAIMED_AT,
                LEASE_DURATION,
                10
            );

        assertThat(thirdBatch)
            .isEmpty();
    }

    private void assertClaimedState(
        UUID eventId,
        String publisherId,
        int attemptCount
    ) {
        EventState state =
            stateOf(eventId);

        assertThat(state.status())
            .isEqualTo("PROCESSING");

        assertThat(state.attemptCount())
            .isEqualTo(attemptCount);

        assertThat(state.lockedBy())
            .isEqualTo(publisherId);

        assertThat(state.lockedAt())
            .isEqualTo(CLAIMED_AT);

        assertThat(state.lockedUntil())
            .isEqualTo(
                CLAIMED_AT.plus(
                    LEASE_DURATION
                )
            );
    }

    private EventState stateOf(
        UUID eventId
    ) {
        return jdbcTemplate.queryForObject(
            """
            SELECT
                status,
                attempt_count,
                locked_at,
                locked_until,
                locked_by,
                last_error
            FROM outbox_events
            WHERE id = ?
            """,
            (resultSet, rowNumber) -> {
                Timestamp lockedAt =
                    resultSet.getTimestamp(
                        "locked_at"
                    );

                Timestamp lockedUntil =
                    resultSet.getTimestamp(
                        "locked_until"
                    );

                return new EventState(
                    resultSet.getString(
                        "status"
                    ),
                    resultSet.getInt(
                        "attempt_count"
                    ),
                    lockedAt == null
                        ? null
                        : lockedAt.toInstant(),
                    lockedUntil == null
                        ? null
                        : lockedUntil.toInstant(),
                    resultSet.getString(
                        "locked_by"
                    ),
                    resultSet.getString(
                        "last_error"
                    )
                );
            },
            eventId
        );
    }

    private void insertEvent(
        UUID eventId,
        String status,
        int attemptCount,
        Instant availableAt,
        Instant lockedAt,
        Instant lockedUntil,
        String lockedBy,
        String lastError
    ) {
        String payload = """
            {
              "eventId": "%s",
              "eventType": "wallet.transfer.completed",
              "eventVersion": 1
            }
            """.formatted(eventId);

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
                ?, ?, ?, ?, ?, ?, ?, ?,
                CAST(? AS jsonb),
                ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            """,
            eventId,
            "PAYMENT_TRANSACTION",
            AGGREGATE_ID,
            EVENT_TYPE,
            1,
            EVENT_TYPE,
            AGGREGATE_ID.toString(),
            EVENT_TYPE + ":1:" + eventId,
            payload,
            status,
            attemptCount,
            Timestamp.from(availableAt),
            timestamp(lockedAt),
            timestamp(lockedUntil),
            lockedBy,
            Timestamp.from(CREATED_AT),
            null,
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

    private record EventState(
        String status,
        int attemptCount,
        Instant lockedAt,
        Instant lockedUntil,
        String lockedBy,
        String lastError
    ) {
    }
}

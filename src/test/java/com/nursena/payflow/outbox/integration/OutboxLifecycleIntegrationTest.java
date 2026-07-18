package com.nursena.payflow.outbox.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.nursena.payflow.outbox.application.port.out.OutboxEventClaimPort;
import com.nursena.payflow.outbox.application.port.out.OutboxEventLifecyclePort;
import com.nursena.payflow.outbox.domain.exception.InvalidOutboxEventStateException;
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
class OutboxLifecycleIntegrationTest {

    private static final UUID EVENT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000000021"
        );

    private static final UUID AGGREGATE_ID =
        UUID.fromString(
            "60000000-0000-0000-0000-000000000021"
        );

    private static final String EVENT_TYPE =
        "wallet.transfer.completed";

    private static final String FIRST_PUBLISHER =
        "publisher-1";

    private static final String SECOND_PUBLISHER =
        "publisher-2";

    private static final Instant CREATED_AT =
        Instant.parse(
            "2026-07-18T12:00:00Z"
        );

    private static final Instant CLAIMED_AT =
        CREATED_AT.plusSeconds(60);

    private static final Duration LEASE_DURATION =
        Duration.ofSeconds(30);

    private static final Instant TRANSITION_AT =
        CLAIMED_AT.plusSeconds(10);

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
            "postgres:17-alpine"
        );

    @Autowired
    private OutboxEventClaimPort claimPort;

    @Autowired
    private OutboxEventLifecyclePort lifecyclePort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpDatabase() {
        jdbcTemplate.update(
            "DELETE FROM outbox_events"
        );

        insertPendingEvent();
    }

    @Test
    void shouldPersistPublishedTransition() {
        claim(
            FIRST_PUBLISHER,
            CLAIMED_AT
        );

        OutboxEvent published =
            lifecyclePort.markPublished(
                EVENT_ID,
                FIRST_PUBLISHER,
                TRANSITION_AT
            );

        assertThat(published.status().name())
            .isEqualTo("PUBLISHED");

        assertThat(published.publishedAt())
            .isEqualTo(TRANSITION_AT);

        EventState state = stateOfEvent();

        assertThat(state.status())
            .isEqualTo("PUBLISHED");

        assertThat(state.attemptCount())
            .isEqualTo(1);

        assertThat(state.publishedAt())
            .isEqualTo(TRANSITION_AT);

        assertThat(state.lockedAt())
            .isNull();

        assertThat(state.lockedUntil())
            .isNull();

        assertThat(state.lockedBy())
            .isNull();

        assertThat(state.lastError())
            .isNull();

        assertThat(
            claimPort.claimAvailable(
                SECOND_PUBLISHER,
                TRANSITION_AT.plusSeconds(60),
                LEASE_DURATION,
                10
            )
        ).isEmpty();
    }

    @Test
    void shouldPersistRetryAndBecomeClaimableAtScheduledTime() {
        claim(
            FIRST_PUBLISHER,
            CLAIMED_AT
        );

        Instant nextAvailableAt =
            TRANSITION_AT.plusSeconds(60);

        OutboxEvent retry =
            lifecyclePort.scheduleRetry(
                EVENT_ID,
                FIRST_PUBLISHER,
                TRANSITION_AT,
                nextAvailableAt,
                "Kafka broker is unavailable."
            );

        assertThat(retry.status().name())
            .isEqualTo("PENDING");

        assertThat(retry.attemptCount())
            .isEqualTo(1);

        assertThat(retry.availableAt())
            .isEqualTo(nextAvailableAt);

        EventState retryState =
            stateOfEvent();

        assertThat(retryState.status())
            .isEqualTo("PENDING");

        assertThat(retryState.attemptCount())
            .isEqualTo(1);

        assertThat(retryState.availableAt())
            .isEqualTo(nextAvailableAt);

        assertThat(retryState.lockedAt())
            .isNull();

        assertThat(retryState.lockedUntil())
            .isNull();

        assertThat(retryState.lockedBy())
            .isNull();

        assertThat(retryState.lastError())
            .isEqualTo(
                "Kafka broker is unavailable."
            );

        List<OutboxEvent> beforeSchedule =
            claimPort.claimAvailable(
                SECOND_PUBLISHER,
                nextAvailableAt.minusSeconds(1),
                LEASE_DURATION,
                10
            );

        assertThat(beforeSchedule)
            .isEmpty();

        List<OutboxEvent> reclaimed =
            claimPort.claimAvailable(
                SECOND_PUBLISHER,
                nextAvailableAt,
                LEASE_DURATION,
                10
            );

        assertThat(reclaimed)
            .singleElement()
            .satisfies(event -> {
                assertThat(event.id())
                    .isEqualTo(EVENT_ID);

                assertThat(event.attemptCount())
                    .isEqualTo(2);

                assertThat(event.lockedBy())
                    .isEqualTo(SECOND_PUBLISHER);

                assertThat(event.lastError())
                    .isEqualTo(
                        "Kafka broker is unavailable."
                    );
            });
    }

    @Test
    void shouldPersistTerminalFailureAndExcludeEventFromClaims() {
        claim(
            FIRST_PUBLISHER,
            CLAIMED_AT
        );

        OutboxEvent failed =
            lifecyclePort.markFailed(
                EVENT_ID,
                FIRST_PUBLISHER,
                TRANSITION_AT,
                "Maximum attempts exceeded."
            );

        assertThat(failed.status().name())
            .isEqualTo("FAILED");

        assertThat(failed.attemptCount())
            .isEqualTo(1);

        EventState state = stateOfEvent();

        assertThat(state.status())
            .isEqualTo("FAILED");

        assertThat(state.attemptCount())
            .isEqualTo(1);

        assertThat(state.lockedAt())
            .isNull();

        assertThat(state.lockedUntil())
            .isNull();

        assertThat(state.lockedBy())
            .isNull();

        assertThat(state.publishedAt())
            .isNull();

        assertThat(state.lastError())
            .isEqualTo(
                "Maximum attempts exceeded."
            );

        assertThat(
            claimPort.claimAvailable(
                SECOND_PUBLISHER,
                TRANSITION_AT.plusSeconds(60),
                LEASE_DURATION,
                10
            )
        ).isEmpty();
    }

    @Test
    void shouldRejectStalePublisherAfterLeaseIsReclaimed() {
        claim(
            FIRST_PUBLISHER,
            CLAIMED_AT
        );

        Instant reclaimedAt =
            CLAIMED_AT
                .plus(LEASE_DURATION)
                .plusSeconds(1);

        List<OutboxEvent> reclaimed =
            claimPort.claimAvailable(
                SECOND_PUBLISHER,
                reclaimedAt,
                LEASE_DURATION,
                1
            );

        assertThat(reclaimed)
            .singleElement()
            .satisfies(event -> {
                assertThat(event.id())
                    .isEqualTo(EVENT_ID);

                assertThat(event.attemptCount())
                    .isEqualTo(2);

                assertThat(event.lockedBy())
                    .isEqualTo(SECOND_PUBLISHER);
            });

        assertThatThrownBy(() ->
            lifecyclePort.markPublished(
                EVENT_ID,
                FIRST_PUBLISHER,
                reclaimedAt.plusSeconds(5)
            )
        )
            .isInstanceOf(
                InvalidOutboxEventStateException.class
            )
            .hasMessage(
                "Outbox event is owned by another publisher."
            );

        EventState state = stateOfEvent();

        assertThat(state.status())
            .isEqualTo("PROCESSING");

        assertThat(state.attemptCount())
            .isEqualTo(2);

        assertThat(state.lockedBy())
            .isEqualTo(SECOND_PUBLISHER);

        assertThat(state.lockedAt())
            .isEqualTo(reclaimedAt);

        assertThat(state.lockedUntil())
            .isEqualTo(
                reclaimedAt.plus(
                    LEASE_DURATION
                )
            );

        assertThat(state.publishedAt())
            .isNull();
    }

    private OutboxEvent claim(
        String publisherId,
        Instant claimedAt
    ) {
        List<OutboxEvent> events =
            claimPort.claimAvailable(
                publisherId,
                claimedAt,
                LEASE_DURATION,
                1
            );

        assertThat(events)
            .hasSize(1);

        return events.getFirst();
    }

    private EventState stateOfEvent() {
        return jdbcTemplate.queryForObject(
            """
            SELECT
                status,
                attempt_count,
                available_at,
                locked_at,
                locked_until,
                locked_by,
                published_at,
                last_error
            FROM outbox_events
            WHERE id = ?
            """,
            (resultSet, rowNumber) ->
                new EventState(
                    resultSet.getString(
                        "status"
                    ),
                    resultSet.getInt(
                        "attempt_count"
                    ),
                    instant(
                        resultSet.getTimestamp(
                            "available_at"
                        )
                    ),
                    instant(
                        resultSet.getTimestamp(
                            "locked_at"
                        )
                    ),
                    instant(
                        resultSet.getTimestamp(
                            "locked_until"
                        )
                    ),
                    resultSet.getString(
                        "locked_by"
                    ),
                    instant(
                        resultSet.getTimestamp(
                            "published_at"
                        )
                    ),
                    resultSet.getString(
                        "last_error"
                    )
                ),
            EVENT_ID
        );
    }

    private void insertPendingEvent() {
        String payload = """
            {
              "eventId": "%s",
              "eventType": "wallet.transfer.completed",
              "eventVersion": 1
            }
            """.formatted(EVENT_ID);

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
                created_at
            )
            VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?,
                CAST(? AS jsonb),
                'PENDING',
                0,
                ?,
                ?
            )
            """,
            EVENT_ID,
            "PAYMENT_TRANSACTION",
            AGGREGATE_ID,
            EVENT_TYPE,
            1,
            EVENT_TYPE,
            AGGREGATE_ID.toString(),
            EVENT_TYPE + ":1:" + EVENT_ID,
            payload,
            Timestamp.from(CREATED_AT),
            Timestamp.from(CREATED_AT)
        );
    }

    private static Instant instant(
        Timestamp timestamp
    ) {
        return timestamp == null
            ? null
            : timestamp.toInstant();
    }

    private record EventState(
        String status,
        int attemptCount,
        Instant availableAt,
        Instant lockedAt,
        Instant lockedUntil,
        String lockedBy,
        Instant publishedAt,
        String lastError
    ) {
    }
}

package com.nursena.payflow.eventprocessing.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.nursena.payflow.eventprocessing.application.model.DiscardKafkaDeadLetterRecordResult;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterDiscardPort;
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
class KafkaDeadLetterDiscardPersistenceIntegrationTest {

    private static final Instant RECEIVED_AT =
        Instant.parse(
            "2026-07-22T20:00:00Z"
        );

    private static final Instant LAST_REPLAYED_AT =
        Instant.parse(
            "2026-07-22T20:05:00Z"
        );

    private static final Instant LEASE_UNTIL =
        Instant.parse(
            "2026-07-22T20:10:00Z"
        );

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
            "postgres:17-alpine"
        );

    @Autowired
    private KafkaDeadLetterDiscardPort discardPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update(
            "DELETE FROM kafka_dead_letter_records"
        );
    }

    @Test
    void shouldDiscardReceivedRecord() {
        UUID recordId =
            UUID.fromString(
                "80000000-0000-0000-0000-000000002301"
            );

        insertRecord(
            recordId,
            "RECEIVED",
            0,
            null,
            null,
            null,
            null
        );

        DiscardKafkaDeadLetterRecordResult result =
            discardPort.discard(recordId);

        assertThat(result.isDiscarded())
            .isTrue();

        assertThat(result.isSuccessful())
            .isTrue();

        assertThat(result.isAlreadyDiscarded())
            .isFalse();

        ReplayState state =
            stateOf(recordId);

        assertThat(state.status())
            .isEqualTo("DISCARDED");

        assertThat(state.replayCount())
            .isZero();

        assertThat(state.lastReplayedAt())
            .isNull();

        assertThat(state.replayLeaseOwner())
            .isNull();

        assertThat(state.replayLeaseUntil())
            .isNull();

        assertThat(state.lastReplayError())
            .isNull();
    }

    @Test
    void shouldDiscardReplayFailedRecord() {
        UUID recordId =
            UUID.fromString(
                "80000000-0000-0000-0000-000000002302"
            );

        insertRecord(
            recordId,
            "REPLAY_FAILED",
            2,
            LAST_REPLAYED_AT,
            null,
            null,
            "Kafka broker rejected publication."
        );

        DiscardKafkaDeadLetterRecordResult result =
            discardPort.discard(recordId);

        assertThat(result.isDiscarded())
            .isTrue();

        ReplayState state =
            stateOf(recordId);

        assertThat(state.status())
            .isEqualTo("DISCARDED");

        /*
         * Discarding is a status transition. Replay
         * history remains available for operations
         * and future auditing.
         */
        assertThat(state.replayCount())
            .isEqualTo(2);

        assertThat(state.lastReplayedAt())
            .isEqualTo(LAST_REPLAYED_AT);

        assertThat(state.lastReplayError())
            .isEqualTo(
                "Kafka broker rejected publication."
            );

        assertThat(state.replayLeaseOwner())
            .isNull();

        assertThat(state.replayLeaseUntil())
            .isNull();
    }

    @Test
    void shouldTreatRepeatedDiscardAsIdempotent() {
        UUID recordId =
            UUID.fromString(
                "80000000-0000-0000-0000-000000002303"
            );

        insertRecord(
            recordId,
            "DISCARDED",
            1,
            LAST_REPLAYED_AT,
            null,
            null,
            "Previous replay failure."
        );

        DiscardKafkaDeadLetterRecordResult first =
            discardPort.discard(recordId);

        DiscardKafkaDeadLetterRecordResult second =
            discardPort.discard(recordId);

        assertThat(first.isAlreadyDiscarded())
            .isTrue();

        assertThat(first.isSuccessful())
            .isTrue();

        assertThat(second.isAlreadyDiscarded())
            .isTrue();

        assertThat(second.isSuccessful())
            .isTrue();

        ReplayState state =
            stateOf(recordId);

        assertThat(state.status())
            .isEqualTo("DISCARDED");

        assertThat(state.replayCount())
            .isEqualTo(1);

        assertThat(state.lastReplayedAt())
            .isEqualTo(LAST_REPLAYED_AT);

        assertThat(state.lastReplayError())
            .isEqualTo(
                "Previous replay failure."
            );
    }

    @Test
    void shouldRejectReplayingAndReplayedRecords() {
        UUID replayingRecordId =
            UUID.fromString(
                "80000000-0000-0000-0000-000000002304"
            );

        UUID replayedRecordId =
            UUID.fromString(
                "80000000-0000-0000-0000-000000002305"
            );

        insertRecord(
            replayingRecordId,
            "REPLAYING",
            1,
            LAST_REPLAYED_AT,
            "replay-worker-1",
            LEASE_UNTIL,
            null
        );

        insertRecord(
            replayedRecordId,
            "REPLAYED",
            1,
            LAST_REPLAYED_AT,
            null,
            null,
            null
        );

        DiscardKafkaDeadLetterRecordResult
            replayingResult =
            discardPort.discard(
                replayingRecordId
            );

        DiscardKafkaDeadLetterRecordResult
            replayedResult =
            discardPort.discard(
                replayedRecordId
            );

        assertThat(
            replayingResult.isNotDiscardable()
        )
            .isTrue();

        assertThat(
            replayedResult.isNotDiscardable()
        )
            .isTrue();

        ReplayState replayingState =
            stateOf(replayingRecordId);

        assertThat(replayingState.status())
            .isEqualTo("REPLAYING");

        assertThat(
            replayingState.replayLeaseOwner()
        )
            .isEqualTo("replay-worker-1");

        assertThat(
            replayingState.replayLeaseUntil()
        )
            .isEqualTo(LEASE_UNTIL);

        assertThat(
            stateOf(replayedRecordId).status()
        )
            .isEqualTo("REPLAYED");
    }

    @Test
    void shouldReturnNotFoundForMissingRecord() {
        UUID missingRecordId =
            UUID.fromString(
                "80000000-0000-0000-0000-000000002306"
            );

        DiscardKafkaDeadLetterRecordResult result =
            discardPort.discard(
                missingRecordId
            );

        assertThat(result.isNotFound())
            .isTrue();

        assertThat(result.isSuccessful())
            .isFalse();

        assertThat(result.isNotDiscardable())
            .isFalse();
    }

    @Test
    void shouldAllowOnlyOneConcurrentDiscardTransition()
        throws Exception {

        UUID recordId =
            UUID.fromString(
                "80000000-0000-0000-0000-000000002307"
            );

        insertRecord(
            recordId,
            "RECEIVED",
            0,
            null,
            null,
            null,
            null
        );

        int operationCount = 8;

        ExecutorService executor =
            Executors.newFixedThreadPool(
                operationCount
            );

        CountDownLatch ready =
            new CountDownLatch(
                operationCount
            );

        CountDownLatch start =
            new CountDownLatch(1);

        List<Future<
            DiscardKafkaDeadLetterRecordResult
            >> futures =
            new ArrayList<>();

        try {
            for (
                int index = 0;
                index < operationCount;
                index++
            ) {
                futures.add(
                    executor.submit(
                        () -> {
                            ready.countDown();

                            boolean started =
                                start.await(
                                    10,
                                    TimeUnit.SECONDS
                                );

                            if (!started) {
                                throw new
                                    IllegalStateException(
                                    "Concurrent discard "
                                        + "test start "
                                        + "timed out."
                                );
                            }

                            return discardPort
                                .discard(recordId);
                        }
                    )
                );
            }

            assertThat(
                ready.await(
                    10,
                    TimeUnit.SECONDS
                )
            )
                .isTrue();

            start.countDown();

            long discardedCount = 0;
            long alreadyDiscardedCount = 0;

            for (
                Future<
                    DiscardKafkaDeadLetterRecordResult
                    > future
                : futures
            ) {
                DiscardKafkaDeadLetterRecordResult
                    result =
                    future.get(
                        10,
                        TimeUnit.SECONDS
                    );

                if (result.isDiscarded()) {
                    discardedCount++;
                } else if (
                    result.isAlreadyDiscarded()
                ) {
                    alreadyDiscardedCount++;
                } else {
                    throw new AssertionError(
                        "Unexpected concurrent "
                            + "discard outcome: "
                            + result.outcome()
                    );
                }
            }

            assertThat(discardedCount)
                .isEqualTo(1);

            assertThat(alreadyDiscardedCount)
                .isEqualTo(
                    operationCount - 1L
                );

            assertThat(stateOf(recordId).status())
                .isEqualTo("DISCARDED");
        } finally {
            executor.shutdownNow();

            assertThat(
                executor.awaitTermination(
                    10,
                    TimeUnit.SECONDS
                )
            )
                .isTrue();
        }
    }

    @Test
    void shouldValidateRecordIdentifier() {
        assertThatThrownBy(
            () -> discardPort.discard(null)
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "recordId must not be null"
            );
    }

    private ReplayState stateOf(
        UUID recordId
    ) {
        return jdbcTemplate.queryForObject(
            """
            SELECT
                status,
                replay_count,
                last_replayed_at,
                replay_lease_owner,
                replay_lease_until,
                last_replay_error
            FROM kafka_dead_letter_records
            WHERE id = ?
            """,
            (resultSet, rowNumber) ->
                new ReplayState(
                    resultSet.getString(
                        "status"
                    ),
                    resultSet.getInt(
                        "replay_count"
                    ),
                    instant(
                        resultSet.getTimestamp(
                            "last_replayed_at"
                        )
                    ),
                    resultSet.getString(
                        "replay_lease_owner"
                    ),
                    instant(
                        resultSet.getTimestamp(
                            "replay_lease_until"
                        )
                    ),
                    resultSet.getString(
                        "last_replay_error"
                    )
                ),
            recordId
        );
    }

    private void insertRecord(
        UUID id,
        String status,
        int replayCount,
        Instant lastReplayedAt,
        String replayLeaseOwner,
        Instant replayLeaseUntil,
        String lastReplayError
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO kafka_dead_letter_records (
                id,
                dlt_topic,
                dlt_partition,
                dlt_offset,
                original_topic,
                original_partition,
                original_offset,
                original_consumer_group,
                record_key,
                payload,
                exception_type,
                exception_message,
                status,
                replay_count,
                received_at,
                last_replayed_at,
                replay_lease_owner,
                replay_lease_until,
                last_replay_error,
                replay_origin_id,
                replay_attempt_base
            )
            VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?
            )
            """,
            id,
            "wallet.transfer.completed.dlt",
            0,
            deadLetterOffset(id),
            "wallet.transfer.completed",
            0,
            10L,
            "payflow-transfer-completed-audit-v1",
            "transaction-id",
            "{}",
            "java.lang.IllegalStateException",
            "Temporary processing failure.",
            status,
            replayCount,
            Timestamp.from(RECEIVED_AT),
            timestamp(lastReplayedAt),
            replayLeaseOwner,
            timestamp(replayLeaseUntil),
            lastReplayError,
            id,
            0
        );
    }

    private static long deadLetterOffset(
        UUID id
    ) {
        return Integer.toUnsignedLong(
            id.hashCode()
        );
    }

    private static Timestamp timestamp(
        Instant value
    ) {
        return value == null
            ? null
            : Timestamp.from(value);
    }

    private static Instant instant(
        Timestamp value
    ) {
        return value == null
            ? null
            : value.toInstant();
    }

    private record ReplayState(
        String status,
        int replayCount,
        Instant lastReplayedAt,
        String replayLeaseOwner,
        Instant replayLeaseUntil,
        String lastReplayError
    ) {
    }
}

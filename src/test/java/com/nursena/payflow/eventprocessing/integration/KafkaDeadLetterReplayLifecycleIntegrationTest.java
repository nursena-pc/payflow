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

import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterReplayLifecyclePort;
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
class KafkaDeadLetterReplayLifecycleIntegrationTest {

    private static final UUID RECORD_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000001601"
        );

    private static final String WORKER_ID =
        "replay-worker-1";

    private static final Instant RECEIVED_AT =
        Instant.parse(
            "2026-07-21T20:00:00Z"
        );

    private static final Instant CLAIMED_AT =
        Instant.parse(
            "2026-07-21T20:05:00Z"
        );

    private static final Instant TRANSITION_AT =
        Instant.parse(
            "2026-07-21T20:06:00Z"
        );

    private static final Instant LEASE_UNTIL =
        Instant.parse(
            "2026-07-21T20:10:00Z"
        );

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
            "postgres:17-alpine"
        );

    @Autowired
    private KafkaDeadLetterReplayLifecyclePort
        lifecyclePort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update(
            "DELETE FROM kafka_dead_letter_records"
        );
    }

    @Test
    void shouldMarkReplayAsReplayed() {
        insertReplayingRecord(
            RECORD_ID,
            WORKER_ID,
            CLAIMED_AT,
            LEASE_UNTIL,
            "Previous replay failure."
        );

        boolean transitioned =
            lifecyclePort.tryMarkReplayed(
                RECORD_ID,
                WORKER_ID,
                TRANSITION_AT
            );

        assertThat(transitioned)
            .isTrue();

        ReplayState state =
            stateOf(RECORD_ID);

        assertThat(state.status())
            .isEqualTo("REPLAYED");

        assertThat(state.replayCount())
            .isEqualTo(1);

        assertThat(state.lastReplayedAt())
            .isEqualTo(CLAIMED_AT);

        assertThat(state.replayLeaseOwner())
            .isNull();

        assertThat(state.replayLeaseUntil())
            .isNull();

        assertThat(state.lastReplayError())
            .isNull();

        /*
         * A terminal record cannot be transitioned
         * a second time.
         */
        assertThat(
            lifecyclePort.tryMarkReplayFailed(
                RECORD_ID,
                WORKER_ID,
                TRANSITION_AT.plusSeconds(1),
                "Late failure."
            )
        )
            .isFalse();

        assertThat(
            stateOf(RECORD_ID).status()
        )
            .isEqualTo("REPLAYED");
    }

    @Test
    void shouldMarkReplayAsFailed() {
        insertReplayingRecord(
            RECORD_ID,
            WORKER_ID,
            CLAIMED_AT,
            LEASE_UNTIL,
            null
        );

        boolean transitioned =
            lifecyclePort.tryMarkReplayFailed(
                RECORD_ID,
                WORKER_ID,
                TRANSITION_AT,
                "Kafka broker rejected publication."
            );

        assertThat(transitioned)
            .isTrue();

        ReplayState state =
            stateOf(RECORD_ID);

        assertThat(state.status())
            .isEqualTo("REPLAY_FAILED");

        assertThat(state.replayCount())
            .isEqualTo(1);

        assertThat(state.lastReplayedAt())
            .isEqualTo(CLAIMED_AT);

        assertThat(state.replayLeaseOwner())
            .isNull();

        assertThat(state.replayLeaseUntil())
            .isNull();

        assertThat(state.lastReplayError())
            .isEqualTo(
                "Kafka broker rejected publication."
            );
    }

    @Test
    void shouldRejectTransitionByAnotherWorker() {
        insertReplayingRecord(
            RECORD_ID,
            WORKER_ID,
            CLAIMED_AT,
            LEASE_UNTIL,
            null
        );

        boolean replayed =
            lifecyclePort.tryMarkReplayed(
                RECORD_ID,
                "replay-worker-2",
                TRANSITION_AT
            );

        boolean failed =
            lifecyclePort.tryMarkReplayFailed(
                RECORD_ID,
                "replay-worker-2",
                TRANSITION_AT,
                "Publication failed."
            );

        assertThat(replayed)
            .isFalse();

        assertThat(failed)
            .isFalse();

        ReplayState state =
            stateOf(RECORD_ID);

        assertThat(state.status())
            .isEqualTo("REPLAYING");

        assertThat(state.replayLeaseOwner())
            .isEqualTo(WORKER_ID);

        assertThat(state.replayLeaseUntil())
            .isEqualTo(LEASE_UNTIL);
    }

    @Test
    void shouldRejectTransitionOutsideActiveLeaseWindow() {
        insertReplayingRecord(
            RECORD_ID,
            WORKER_ID,
            CLAIMED_AT,
            LEASE_UNTIL,
            null
        );

        /*
         * A lifecycle result cannot occur before
         * the replay attempt was claimed.
         */
        assertThat(
            lifecyclePort.tryMarkReplayed(
                RECORD_ID,
                WORKER_ID,
                CLAIMED_AT.minusSeconds(1)
            )
        )
            .isFalse();

        /*
         * The lease is no longer active at the
         * exact lease-expiration instant.
         */
        assertThat(
            lifecyclePort.tryMarkReplayFailed(
                RECORD_ID,
                WORKER_ID,
                LEASE_UNTIL,
                "Late publication failure."
            )
        )
            .isFalse();

        ReplayState state =
            stateOf(RECORD_ID);

        assertThat(state.status())
            .isEqualTo("REPLAYING");

        assertThat(state.replayCount())
            .isEqualTo(1);

        assertThat(state.replayLeaseOwner())
            .isEqualTo(WORKER_ID);
    }

    @Test
    void shouldAllowOnlyOneConcurrentTerminalTransition()
        throws Exception {

        insertReplayingRecord(
            RECORD_ID,
            WORKER_ID,
            CLAIMED_AT,
            LEASE_UNTIL,
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

        List<Future<Boolean>> futures =
            new ArrayList<>();

        try {
            for (
                int index = 0;
                index < operationCount;
                index++
            ) {
                int operationIndex = index;

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
                                    "Concurrent lifecycle "
                                        + "test start "
                                        + "timed out."
                                );
                            }

                            if (
                                operationIndex % 2 == 0
                            ) {
                                return lifecyclePort
                                    .tryMarkReplayed(
                                        RECORD_ID,
                                        WORKER_ID,
                                        TRANSITION_AT
                                    );
                            }

                            return lifecyclePort
                                .tryMarkReplayFailed(
                                    RECORD_ID,
                                    WORKER_ID,
                                    TRANSITION_AT,
                                    "Concurrent publication "
                                        + "failure "
                                        + operationIndex
                                );
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

            long successfulTransitions = 0;

            for (
                Future<Boolean> future
                : futures
            ) {
                if (
                    future.get(
                        10,
                        TimeUnit.SECONDS
                    )
                ) {
                    successfulTransitions++;
                }
            }

            assertThat(successfulTransitions)
                .isEqualTo(1);

            ReplayState state =
                stateOf(RECORD_ID);

            assertThat(state.status())
                .isIn(
                    "REPLAYED",
                    "REPLAY_FAILED"
                );

            assertThat(state.replayCount())
                .isEqualTo(1);

            assertThat(state.replayLeaseOwner())
                .isNull();

            assertThat(state.replayLeaseUntil())
                .isNull();

            if (
                state.status()
                    .equals("REPLAYED")
            ) {
                assertThat(
                    state.lastReplayError()
                )
                    .isNull();
            } else {
                assertThat(
                    state.lastReplayError()
                )
                    .startsWith(
                        "Concurrent publication "
                            + "failure "
                    );
            }
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
    void shouldValidateLifecycleArguments() {
        assertThatThrownBy(
            () ->
                lifecyclePort.tryMarkReplayed(
                    null,
                    WORKER_ID,
                    TRANSITION_AT
                )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "recordId must not be null"
            );

        assertThatThrownBy(
            () ->
                lifecyclePort.tryMarkReplayed(
                    RECORD_ID,
                    " ",
                    TRANSITION_AT
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "workerId must not be blank."
            );

        assertThatThrownBy(
            () ->
                lifecyclePort.tryMarkReplayed(
                    RECORD_ID,
                    WORKER_ID,
                    null
                )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "completedAt must not be null"
            );

        assertThatThrownBy(
            () ->
                lifecyclePort.tryMarkReplayFailed(
                    RECORD_ID,
                    WORKER_ID,
                    TRANSITION_AT,
                    " "
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "error must not be blank."
            );
    }

    private void insertReplayingRecord(
        UUID id,
        String workerId,
        Instant claimedAt,
        Instant leaseUntil,
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
            25L,
            "wallet.transfer.completed",
            0,
            10L,
            "payflow-transfer-completed-audit-v1",
            "transaction-id",
            """
            {
              "eventId":
                "80000000-0000-0000-0000-000000001602"
            }
            """,
            "java.lang.IllegalStateException",
            "Temporary processing failure.",
            "REPLAYING",
            1,
            Timestamp.from(RECEIVED_AT),
            Timestamp.from(claimedAt),
            workerId,
            Timestamp.from(leaseUntil),
            lastReplayError,
            id,
            0
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

    private static Instant instant(
        Timestamp timestamp
    ) {
        return timestamp == null
            ? null
            : timestamp.toInstant();
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

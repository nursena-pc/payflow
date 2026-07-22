package com.nursena.payflow.eventprocessing.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.nursena.payflow.eventprocessing.application.model.ClaimKafkaDeadLetterRecordResult;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterReplayRepositoryPort;
import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecord;
import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecordStatus;
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
class KafkaDeadLetterReplayClaimIntegrationTest {

    private static final String DLT_TOPIC =
        "wallet.transfer.completed.dlt";

    private static final String ORIGINAL_TOPIC =
        "wallet.transfer.completed";

    private static final UUID ORIGIN_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000001400"
        );

    private static final Instant RECEIVED_AT =
        Instant.parse(
            "2026-07-21T20:00:00Z"
        );

    private static final Instant CLAIMED_AT =
        Instant.parse(
            "2026-07-21T20:10:00Z"
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
    private KafkaDeadLetterReplayRepositoryPort
        repositoryPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update(
            "DELETE FROM kafka_dead_letter_records"
        );
    }

    @Test
    void shouldClaimReceivedRecord() {
        UUID recordId =
            UUID.fromString(
                "80000000-0000-0000-0000-000000001401"
            );

        insertRecord(
            recordId,
            41L,
            ORIGINAL_TOPIC,
            "{}",
            "RECEIVED",
            0,
            null,
            null,
            null,
            "Previous failure.",
            recordId,
            0
        );

        ClaimKafkaDeadLetterRecordResult result =
            repositoryPort.tryClaim(
                recordId,
                "replay-worker-1",
                CLAIMED_AT,
                LEASE_DURATION,
                3
            );

        assertThat(result.isClaimed())
            .isTrue();

        KafkaDeadLetterRecord claimed =
            result.record();

        assertThat(claimed)
            .isNotNull();

        assertThat(claimed.status())
            .isEqualTo(
                KafkaDeadLetterRecordStatus.REPLAYING
            );

        assertThat(claimed.replayCount())
            .isEqualTo(1);

        assertThat(claimed.lastReplayedAt())
            .isEqualTo(CLAIMED_AT);

        assertThat(claimed.replayLeaseOwner())
            .isEqualTo("replay-worker-1");

        assertThat(claimed.replayLeaseUntil())
            .isEqualTo(
                CLAIMED_AT.plus(
                    LEASE_DURATION
                )
            );

        assertThat(claimed.lastReplayError())
            .isNull();

        assertThat(claimed.replayOriginId())
            .isEqualTo(recordId);

        assertThat(claimed.replayAttemptBase())
            .isZero();

        assertClaimedState(
            recordId,
            "replay-worker-1",
            1
        );
    }

    @Test
    void shouldRejectActiveLeaseAndReclaimExpiredLease() {
        UUID activeRecordId =
            UUID.fromString(
                "80000000-0000-0000-0000-000000001402"
            );

        UUID expiredRecordId =
            UUID.fromString(
                "80000000-0000-0000-0000-000000001403"
            );

        insertRecord(
            activeRecordId,
            42L,
            ORIGINAL_TOPIC,
            "{}",
            "REPLAYING",
            1,
            CLAIMED_AT.minusSeconds(10),
            "active-worker",
            CLAIMED_AT.plusSeconds(10),
            null,
            activeRecordId,
            0
        );

        insertRecord(
            expiredRecordId,
            43L,
            ORIGINAL_TOPIC,
            "{}",
            "REPLAYING",
            1,
            CLAIMED_AT.minusSeconds(60),
            "expired-worker",
            CLAIMED_AT.minusSeconds(1),
            "Previous replay failure.",
            expiredRecordId,
            0
        );

        ClaimKafkaDeadLetterRecordResult
            activeLeaseResult =
            repositoryPort.tryClaim(
                activeRecordId,
                "new-worker",
                CLAIMED_AT,
                LEASE_DURATION,
                3
            );

        assertThat(
            activeLeaseResult.isNotClaimable()
        )
            .isTrue();

        ClaimKafkaDeadLetterRecordResult reclaimed =
            repositoryPort.tryClaim(
                expiredRecordId,
                "new-worker",
                CLAIMED_AT,
                LEASE_DURATION,
                3
            );

        assertThat(reclaimed.isClaimed())
            .isTrue();

        assertThat(
            reclaimed.record().replayCount()
        )
            .isEqualTo(2);

        assertClaimedState(
            expiredRecordId,
            "new-worker",
            2
        );

        ReplayState activeState =
            stateOf(activeRecordId);

        assertThat(activeState.replayLeaseOwner())
            .isEqualTo("active-worker");

        assertThat(activeState.replayCount())
            .isEqualTo(1);
    }

    @Test
    void shouldEnforceMaximumAttemptsAcrossReplayLineage() {
        UUID derivedRecordId =
            UUID.fromString(
                "80000000-0000-0000-0000-000000001404"
            );

        insertRecord(
            derivedRecordId,
            44L,
            ORIGINAL_TOPIC,
            "{}",
            "RECEIVED",
            0,
            null,
            null,
            null,
            null,
            ORIGIN_ID,
            2
        );

        ClaimKafkaDeadLetterRecordResult firstClaim =
            repositoryPort.tryClaim(
                derivedRecordId,
                "replay-worker-1",
                CLAIMED_AT,
                LEASE_DURATION,
                3
            );

        assertThat(firstClaim.isClaimed())
            .isTrue();

        assertThat(
            firstClaim.record()
                .replayAttemptBase()
        )
            .isEqualTo(2);

        assertThat(
            firstClaim.record()
                .replayCount()
        )
            .isEqualTo(1);

        ClaimKafkaDeadLetterRecordResult secondClaim =
            repositoryPort.tryClaim(
                derivedRecordId,
                "replay-worker-2",
                CLAIMED_AT.plus(
                    LEASE_DURATION
                ),
                LEASE_DURATION,
                3
            );

        assertThat(secondClaim.isNotClaimable())
            .isTrue();

        assertThat(
            stateOf(derivedRecordId)
                .replayCount()
        )
            .isEqualTo(1);
    }

    @Test
    void shouldRejectRecordsWithoutReplayablePayloadOrSource() {
        UUID nullPayloadId =
            UUID.fromString(
                "80000000-0000-0000-0000-000000001405"
            );

        UUID deadLetterSourceId =
            UUID.fromString(
                "80000000-0000-0000-0000-000000001406"
            );

        insertRecord(
            nullPayloadId,
            45L,
            ORIGINAL_TOPIC,
            null,
            "RECEIVED",
            0,
            null,
            null,
            null,
            null,
            nullPayloadId,
            0
        );

        insertRecord(
            deadLetterSourceId,
            46L,
            DLT_TOPIC,
            "{}",
            "RECEIVED",
            0,
            null,
            null,
            null,
            null,
            deadLetterSourceId,
            0
        );

        ClaimKafkaDeadLetterRecordResult
            nullPayloadResult =
            repositoryPort.tryClaim(
                nullPayloadId,
                "replay-worker-1",
                CLAIMED_AT,
                LEASE_DURATION,
                3
            );

        assertThat(
            nullPayloadResult.isNotClaimable()
        )
            .isTrue();

        ClaimKafkaDeadLetterRecordResult
            deadLetterSourceResult =
            repositoryPort.tryClaim(
                deadLetterSourceId,
                "replay-worker-1",
                CLAIMED_AT,
                LEASE_DURATION,
                3
            );

        assertThat(
            deadLetterSourceResult.isNotClaimable()
        )
            .isTrue();
    }

    @Test
    void shouldDistinguishMissingRecordFromUnclaimableRecord() {
        UUID missingRecordId =
            UUID.fromString(
                "80000000-0000-0000-0000-000000001408"
            );

        UUID replayedRecordId =
            UUID.fromString(
                "80000000-0000-0000-0000-000000001409"
            );

        insertRecord(
            replayedRecordId,
            49L,
            ORIGINAL_TOPIC,
            "{}",
            "REPLAYED",
            1,
            CLAIMED_AT.minusSeconds(30),
            null,
            null,
            null,
            replayedRecordId,
            0
        );

        ClaimKafkaDeadLetterRecordResult
            missingResult =
            repositoryPort.tryClaim(
                missingRecordId,
                "replay-worker-1",
                CLAIMED_AT,
                LEASE_DURATION,
                3
            );

        ClaimKafkaDeadLetterRecordResult
            unclaimableResult =
            repositoryPort.tryClaim(
                replayedRecordId,
                "replay-worker-1",
                CLAIMED_AT,
                LEASE_DURATION,
                3
            );

        assertThat(missingResult.isNotFound())
            .isTrue();

        assertThat(
            unclaimableResult.isNotClaimable()
        )
            .isTrue();

        assertThat(
            stateOf(replayedRecordId).status()
        )
            .isEqualTo("REPLAYED");
    }

    @Test
    void shouldAllowOnlyOneConcurrentClaim()
        throws Exception {

        UUID recordId =
            UUID.fromString(
                "80000000-0000-0000-0000-000000001407"
            );

        insertRecord(
            recordId,
            47L,
            ORIGINAL_TOPIC,
            "{}",
            "RECEIVED",
            0,
            null,
            null,
            null,
            null,
            recordId,
            0
        );

        int workerCount = 8;

        ExecutorService executor =
            Executors.newFixedThreadPool(
                workerCount
            );

        CountDownLatch ready =
            new CountDownLatch(
                workerCount
            );

        CountDownLatch start =
            new CountDownLatch(1);

        List<Future<ClaimKafkaDeadLetterRecordResult>>
            futures =
            new ArrayList<>();

        try {
            for (
                int index = 0;
                index < workerCount;
                index++
            ) {
                String workerId =
                    "replay-worker-" + index;

                futures.add(
                    executor.submit(
                        () -> {
                            ready.countDown();

                            if (
                                !start.await(
                                    10,
                                    TimeUnit.SECONDS
                                )
                            ) {
                                throw new
                                    IllegalStateException(
                                    "Concurrent claim "
                                        + "start timed out."
                                );
                            }

                            return repositoryPort
                                .tryClaim(
                                    recordId,
                                    workerId,
                                    CLAIMED_AT,
                                    LEASE_DURATION,
                                    3
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

            long successfulClaims = 0;

            for (
                Future<ClaimKafkaDeadLetterRecordResult>
                    future
                : futures
            ) {
                if (
                    future.get(
                        10,
                        TimeUnit.SECONDS
                    ).isClaimed()
                ) {
                    successfulClaims++;
                }
            }

            assertThat(successfulClaims)
                .isEqualTo(1);

            assertThat(
                stateOf(recordId).replayCount()
            )
                .isEqualTo(1);
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

    private void assertClaimedState(
        UUID recordId,
        String workerId,
        int replayCount
    ) {
        ReplayState state =
            stateOf(recordId);

        assertThat(state.status())
            .isEqualTo("REPLAYING");

        assertThat(state.replayCount())
            .isEqualTo(replayCount);

        assertThat(state.lastReplayedAt())
            .isEqualTo(CLAIMED_AT);

        assertThat(state.replayLeaseOwner())
            .isEqualTo(workerId);

        assertThat(state.replayLeaseUntil())
            .isEqualTo(
                CLAIMED_AT.plus(
                    LEASE_DURATION
                )
            );

        assertThat(state.lastReplayError())
            .isNull();
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
                    resultSet.getTimestamp(
                        "last_replayed_at"
                    ) == null
                        ? null
                        : resultSet.getTimestamp(
                        "last_replayed_at"
                    ).toInstant(),
                    resultSet.getString(
                        "replay_lease_owner"
                    ),
                    resultSet.getTimestamp(
                        "replay_lease_until"
                    ) == null
                        ? null
                        : resultSet.getTimestamp(
                        "replay_lease_until"
                    ).toInstant(),
                    resultSet.getString(
                        "last_replay_error"
                    )
                ),
            recordId
        );
    }

    private void insertRecord(
        UUID id,
        long deadLetterOffset,
        String originalTopic,
        String payload,
        String status,
        int replayCount,
        Instant lastReplayedAt,
        String replayLeaseOwner,
        Instant replayLeaseUntil,
        String lastReplayError,
        UUID replayOriginId,
        int replayAttemptBase
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
            DLT_TOPIC,
            0,
            deadLetterOffset,
            originalTopic,
            0,
            10L,
            "payflow-transfer-completed-audit-v1",
            "transaction-id",
            payload,
            "IllegalStateException",
            "Temporary failure.",
            status,
            replayCount,
            Timestamp.from(RECEIVED_AT),
            timestamp(lastReplayedAt),
            replayLeaseOwner,
            timestamp(replayLeaseUntil),
            lastReplayError,
            replayOriginId,
            replayAttemptBase
        );
    }

    private static Timestamp timestamp(
        Instant value
    ) {
        return value == null
            ? null
            : Timestamp.from(value);
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

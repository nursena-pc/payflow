package com.nursena.payflow.eventprocessing.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterRecordDetails;
import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterRecordFilter;
import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterRecordPage;
import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterRecordSummary;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterQueryPort;
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
class KafkaDeadLetterQueryPersistenceIntegrationTest {

    private static final UUID ORIGIN_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000001200"
        );

    private static final UUID RECEIVED_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000001201"
        );

    private static final UUID FAILED_LOW_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000001202"
        );

    private static final UUID FAILED_HIGH_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000001203"
        );

    private static final UUID REPLAYING_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000001204"
        );

    private static final Instant BASE_TIME =
        Instant.parse(
            "2026-07-22T12:00:00Z"
        );

    private static final Instant
        REPLAYING_LEASE_UNTIL =
        BASE_TIME.plusSeconds(240);

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
            "postgres:17-alpine"
        );

    @Autowired
    private KafkaDeadLetterQueryPort queryPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void prepareDatabase() {
        jdbcTemplate.update(
            "DELETE FROM kafka_dead_letter_records"
        );

        insertFixtures();
    }

    @Test
    void shouldListAllRecordsUsingDeterministicPagination() {
        KafkaDeadLetterRecordPage firstPage =
            queryPort.findPage(
                0,
                2,
                KafkaDeadLetterRecordFilter
                    .unfiltered()
            );

        assertThat(firstPage.items())
            .extracting(
                KafkaDeadLetterRecordSummary::id
            )
            .containsExactly(
                FAILED_HIGH_ID,
                FAILED_LOW_ID
            );

        assertThat(firstPage.page())
            .isZero();

        assertThat(firstPage.size())
            .isEqualTo(2);

        assertThat(firstPage.totalElements())
            .isEqualTo(4L);

        assertThat(firstPage.totalPages())
            .isEqualTo(2);

        assertThat(firstPage.first())
            .isTrue();

        assertThat(firstPage.last())
            .isFalse();

        assertThat(firstPage.hasNext())
            .isTrue();

        assertThat(firstPage.hasPrevious())
            .isFalse();

        KafkaDeadLetterRecordPage secondPage =
            queryPort.findPage(
                1,
                2,
                KafkaDeadLetterRecordFilter
                    .unfiltered()
            );

        assertThat(secondPage.items())
            .extracting(
                KafkaDeadLetterRecordSummary::id
            )
            .containsExactly(
                REPLAYING_ID,
                RECEIVED_ID
            );

        assertThat(secondPage.first())
            .isFalse();

        assertThat(secondPage.last())
            .isTrue();

        assertThat(secondPage.hasNext())
            .isFalse();

        assertThat(secondPage.hasPrevious())
            .isTrue();
    }

    @Test
    void shouldFilterRecordsByStatus() {
        KafkaDeadLetterRecordFilter filter =
            new KafkaDeadLetterRecordFilter(
                KafkaDeadLetterRecordStatus
                    .REPLAY_FAILED
            );

        KafkaDeadLetterRecordPage firstPage =
            queryPort.findPage(
                0,
                1,
                filter
            );

        assertThat(firstPage.items())
            .extracting(
                KafkaDeadLetterRecordSummary::id
            )
            .containsExactly(
                FAILED_HIGH_ID
            );

        assertThat(firstPage.totalElements())
            .isEqualTo(2L);

        assertThat(firstPage.totalPages())
            .isEqualTo(2);

        KafkaDeadLetterRecordPage secondPage =
            queryPort.findPage(
                1,
                1,
                filter
            );

        assertThat(secondPage.items())
            .extracting(
                KafkaDeadLetterRecordSummary::id
            )
            .containsExactly(
                FAILED_LOW_ID
            );
    }

    @Test
    void shouldReturnSafeRecordDetails() {
        KafkaDeadLetterRecordDetails details =
            queryPort.findById(
                    FAILED_HIGH_ID
                )
                .orElseThrow();

        KafkaDeadLetterRecordSummary summary =
            details.summary();

        assertThat(summary.id())
            .isEqualTo(FAILED_HIGH_ID);

        assertThat(summary.status())
            .isEqualTo(
                KafkaDeadLetterRecordStatus
                    .REPLAY_FAILED
            );

        assertThat(summary.deadLetterTopic())
            .isEqualTo(
                "wallet.transfer.completed.dlt"
            );

        assertThat(summary.originalTopic())
            .isEqualTo(
                "wallet.transfer.completed"
            );

        assertThat(summary.replayCount())
            .isEqualTo(1);

        assertThat(summary.replayAttemptBase())
            .isEqualTo(2);

        assertThat(summary.totalReplayAttempts())
            .isEqualTo(3);

        assertThat(summary.replayOriginId())
            .isEqualTo(ORIGIN_ID);

        assertThat(summary.payloadAvailable())
            .isTrue();

        assertThat(details.exceptionMessage())
            .isEqualTo(
                "Failure for record "
                    + FAILED_HIGH_ID
            );

        assertThat(details.lastReplayError())
            .isEqualTo(
                "Replay publication failed."
            );

        assertThat(details.replayLeaseUntil())
            .isNull();
    }

    @Test
    void shouldReturnReplayLeaseExpiration() {
        KafkaDeadLetterRecordDetails details =
            queryPort.findById(
                    REPLAYING_ID
                )
                .orElseThrow();

        assertThat(details.summary().status())
            .isEqualTo(
                KafkaDeadLetterRecordStatus
                    .REPLAYING
            );

        assertThat(details.replayLeaseUntil())
            .isEqualTo(
                REPLAYING_LEASE_UNTIL
            );

        assertThat(details.lastReplayError())
            .isNull();
    }

    @Test
    void shouldTreatBlankPayloadAsUnavailable() {
        KafkaDeadLetterRecordDetails details =
            queryPort.findById(
                    FAILED_LOW_ID
                )
                .orElseThrow();

        assertThat(
            details.summary()
                .payloadAvailable()
        )
            .isFalse();
    }

    @Test
    void shouldReturnEmptyForUnknownIdentifier() {
        assertThat(
            queryPort.findById(
                UUID.fromString(
                    "80000000-0000-0000-0000-000000001299"
                )
            )
        )
            .isEmpty();
    }

    @Test
    void shouldCreateQueryIndexes() {
        Map<String, String> definitions =
            indexDefinitions();

        assertThat(definitions)
            .containsKeys(
                "idx_kafka_dead_letter_records_"
                    + "status_received_id",
                "idx_kafka_dead_letter_records_"
                    + "received_id"
            );

        assertThat(definitions)
            .doesNotContainKey(
                "idx_kafka_dead_letter_records_"
                    + "status_received"
            );

        assertThat(
            definitions.get(
                "idx_kafka_dead_letter_records_"
                    + "status_received_id"
            )
        )
            .contains(
                "(status, received_at DESC, id DESC)"
            );

        assertThat(
            definitions.get(
                "idx_kafka_dead_letter_records_"
                    + "received_id"
            )
        )
            .contains(
                "(received_at DESC, id DESC)"
            );
    }

    private void insertFixtures() {
        insertRecord(
            RECEIVED_ID,
            201L,
            BASE_TIME,
            null,
            "RECEIVED",
            0,
            null,
            null,
            null,
            null,
            RECEIVED_ID,
            0
        );

        insertRecord(
            REPLAYING_ID,
            204L,
            BASE_TIME.plusSeconds(60),
            "{}",
            "REPLAYING",
            2,
            BASE_TIME.plusSeconds(180),
            "replay-worker-1",
            REPLAYING_LEASE_UNTIL,
            null,
            REPLAYING_ID,
            0
        );

        insertRecord(
            FAILED_LOW_ID,
            202L,
            BASE_TIME.plusSeconds(120),
            "   ",
            "REPLAY_FAILED",
            1,
            BASE_TIME.plusSeconds(150),
            null,
            null,
            "First replay failure.",
            FAILED_LOW_ID,
            0
        );

        insertRecord(
            FAILED_HIGH_ID,
            203L,
            BASE_TIME.plusSeconds(120),
            "{}",
            "REPLAY_FAILED",
            1,
            BASE_TIME.plusSeconds(160),
            null,
            null,
            "Replay publication failed.",
            ORIGIN_ID,
            2
        );
    }

    private void insertRecord(
        UUID id,
        long deadLetterOffset,
        Instant receivedAt,
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
            "wallet.transfer.completed.dlt",
            0,
            deadLetterOffset,
            "wallet.transfer.completed",
            0,
            deadLetterOffset - 100,
            "payflow-transfer-completed-audit-v1",
            "sensitive-record-key-" + id,
            payload,
            "java.lang.IllegalStateException",
            "Failure for record " + id,
            status,
            replayCount,
            Timestamp.from(receivedAt),
            timestamp(lastReplayedAt),
            replayLeaseOwner,
            timestamp(replayLeaseUntil),
            lastReplayError,
            replayOriginId,
            replayAttemptBase
        );
    }

    private Map<String, String>
    indexDefinitions() {
        return jdbcTemplate.query(
            """
            SELECT
                indexname,
                indexdef
            FROM pg_indexes
            WHERE schemaname = current_schema()
              AND tablename =
                    'kafka_dead_letter_records'
            """,
            resultSet -> {
                Map<String, String> result =
                    new LinkedHashMap<>();

                while (resultSet.next()) {
                    result.put(
                        resultSet.getString(
                            "indexname"
                        ),
                        resultSet.getString(
                            "indexdef"
                        )
                    );
                }

                return result;
            }
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

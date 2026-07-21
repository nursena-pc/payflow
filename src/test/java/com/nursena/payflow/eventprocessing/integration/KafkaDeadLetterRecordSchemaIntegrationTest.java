package com.nursena.payflow.eventprocessing.integration;

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
class KafkaDeadLetterRecordSchemaIntegrationTest {

    private static final UUID RECORD_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000001001"
        );

    private static final Instant RECEIVED_AT =
        Instant.parse(
            "2026-07-21T16:00:00Z"
        );

    private static final Instant REPLAYED_AT =
        Instant.parse(
            "2026-07-21T16:05:00Z"
        );

    private static final Instant LEASE_UNTIL =
        Instant.parse(
            "2026-07-21T16:10:00Z"
        );

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
            "DELETE FROM kafka_dead_letter_records"
        );
    }

    @Test
    void shouldPersistReceivedRecordWithNullableContent() {
        insertRecord(
            RECORD_ID,
            "wallet.transfer.completed.dlt",
            0,
            25L,
            "wallet.transfer.completed",
            0,
            10L,
            "RECEIVED",
            0,
            null,
            null,
            null
        );

        var stored =
            jdbcTemplate.queryForMap(
                """
                SELECT
                    record_key,
                    payload,
                    status,
                    replay_count
                FROM kafka_dead_letter_records
                WHERE id = ?
                """,
                RECORD_ID
            );

        assertThat(
            stored.get("record_key")
        )
            .isNull();

        assertThat(
            stored.get("payload")
        )
            .isNull();

        assertThat(
            stored.get("status")
        )
            .isEqualTo("RECEIVED");

        assertThat(
            stored.get("replay_count")
        )
            .isEqualTo(0);
    }

    @Test
    void shouldRejectDuplicateDeadLetterLocation() {
        insertReceivedRecord(
            RECORD_ID
        );

        assertConstraintViolation(
            () -> insertReceivedRecord(
                UUID.fromString(
                    "80000000-0000-0000-0000-000000001002"
                )
            ),
            "uq_kafka_dead_letter_records_location"
        );
    }

    @Test
    void shouldRejectNegativeDeadLetterPartition() {
        assertConstraintViolation(
            () -> insertRecord(
                RECORD_ID,
                "wallet.transfer.completed.dlt",
                -1,
                25L,
                "wallet.transfer.completed",
                0,
                10L,
                "RECEIVED",
                0,
                null,
                null,
                null
            ),
            "chk_kafka_dead_letter_records_dlt_partition"
        );
    }

    @Test
    void shouldRejectInvalidStatus() {
        assertConstraintViolation(
            () -> insertRecord(
                RECORD_ID,
                "wallet.transfer.completed.dlt",
                0,
                25L,
                "wallet.transfer.completed",
                0,
                10L,
                "UNKNOWN",
                0,
                null,
                null,
                null
            ),
            "chk_kafka_dead_letter_records_status"
        );
    }

    @Test
    void shouldRejectReplayCountWithoutTimestamp() {
        assertConstraintViolation(
            () -> insertRecord(
                RECORD_ID,
                "wallet.transfer.completed.dlt",
                0,
                25L,
                "wallet.transfer.completed",
                0,
                10L,
                "REPLAY_FAILED",
                1,
                null,
                null,
                null
            ),
            "chk_kafka_dead_letter_records_replay_time"
        );
    }

    @Test
    void shouldRejectReplayingRecordWithoutLease() {
        assertConstraintViolation(
            () -> insertRecord(
                RECORD_ID,
                "wallet.transfer.completed.dlt",
                0,
                25L,
                "wallet.transfer.completed",
                0,
                10L,
                "REPLAYING",
                1,
                REPLAYED_AT,
                null,
                null
            ),
            "chk_kafka_dead_letter_records_replay_lease"
        );
    }

    @Test
    void shouldPersistValidReplayingRecord() {
        insertRecord(
            RECORD_ID,
            "wallet.transfer.completed.dlt",
            0,
            25L,
            "wallet.transfer.completed",
            0,
            10L,
            "REPLAYING",
            1,
            REPLAYED_AT,
            "replay-worker-1",
            LEASE_UNTIL
        );

        String status =
            jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM kafka_dead_letter_records
                WHERE id = ?
                """,
                String.class,
                RECORD_ID
            );

        assertThat(status)
            .isEqualTo("REPLAYING");
    }

    private void insertReceivedRecord(
        UUID id
    ) {
        insertRecord(
            id,
            "wallet.transfer.completed.dlt",
            0,
            25L,
            "wallet.transfer.completed",
            0,
            10L,
            "RECEIVED",
            0,
            null,
            null,
            null
        );
    }

    private void insertRecord(
        UUID id,
        String deadLetterTopic,
        int deadLetterPartition,
        long deadLetterOffset,
        String originalTopic,
        int originalPartition,
        long originalOffset,
        String status,
        int replayCount,
        Instant lastReplayedAt,
        String replayLeaseOwner,
        Instant replayLeaseUntil
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
                last_replay_error
            )
            VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            """,
            id,
            deadLetterTopic,
            deadLetterPartition,
            deadLetterOffset,
            originalTopic,
            originalPartition,
            originalOffset,
            "payflow-transfer-completed-audit-v1",
            null,
            null,
            "IllegalStateException",
            "Temporary failure.",
            status,
            replayCount,
            Timestamp.from(RECEIVED_AT),
            timestamp(lastReplayedAt),
            replayLeaseOwner,
            timestamp(replayLeaseUntil),
            null
        );
    }

    private static Timestamp timestamp(
        Instant value
    ) {
        if (value == null) {
            return null;
        }

        return Timestamp.from(value);
    }

    private static void assertConstraintViolation(
        ThrowingCallable operation,
        String constraintName
    ) {
        Throwable thrown =
            catchThrowable(operation);

        assertThat(thrown)
            .isInstanceOf(
                DataIntegrityViolationException.class
            );

        assertThat(
            rootCauseOf(thrown).getMessage()
        )
            .contains(constraintName);
    }

    private static Throwable rootCauseOf(
        Throwable throwable
    ) {
        Throwable current = throwable;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        return current;
    }
}

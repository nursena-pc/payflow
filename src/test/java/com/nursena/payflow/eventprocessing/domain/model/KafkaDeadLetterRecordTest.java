package com.nursena.payflow.eventprocessing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class KafkaDeadLetterRecordTest {

    private static final UUID RECORD_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000000901"
        );

    private static final Instant RECEIVED_AT =
        Instant.parse(
            "2026-07-21T15:00:00Z"
        );

    private static final Instant REPLAYED_AT =
        Instant.parse(
            "2026-07-21T15:05:00Z"
        );

    private static final Instant LEASE_UNTIL =
        Instant.parse(
            "2026-07-21T15:10:00Z"
        );

    @Test
    void shouldCreateReceivedRecordWithNullableKeyAndPayload() {
        KafkaDeadLetterRecord record =
            receivedRecord(
                RECORD_ID
            );

        assertThat(record.id())
            .isEqualTo(RECORD_ID);

        assertThat(record.deadLetterTopic())
            .isEqualTo(
                "wallet.transfer.completed.dlt"
            );

        assertThat(record.deadLetterPartition())
            .isZero();

        assertThat(record.deadLetterOffset())
            .isEqualTo(25L);

        assertThat(record.recordKey())
            .isNull();

        assertThat(record.payload())
            .isNull();

        assertThat(record.status())
            .isEqualTo(
                KafkaDeadLetterRecordStatus
                    .RECEIVED
            );

        assertThat(record.replayCount())
            .isZero();

        assertThat(record.receivedAt())
            .isEqualTo(RECEIVED_AT);

        assertThat(record.replayOriginId())
            .isEqualTo(RECORD_ID);

        assertThat(record.replayAttemptBase())
            .isZero();
    }

    @Test
    void shouldCreateReplayDerivedReceivedRecord() {
        UUID derivedRecordId =
            UUID.fromString(
                "80000000-0000-0000-0000-000000000902"
            );

        KafkaDeadLetterRecord record =
            new KafkaDeadLetterRecord(
                derivedRecordId,
                "wallet.transfer.completed.dlt",
                0,
                30L,
                "wallet.transfer.completed",
                0,
                10L,
                "payflow-transfer-completed-audit-v1",
                "transaction-id",
                "{}",
                "IllegalStateException",
                "Temporary failure.",
                KafkaDeadLetterRecordStatus.RECEIVED,
                0,
                RECEIVED_AT,
                null,
                null,
                null,
                null,
                RECORD_ID,
                2
            );

        assertThat(record.replayOriginId())
            .isEqualTo(RECORD_ID);

        assertThat(record.replayAttemptBase())
            .isEqualTo(2);
    }

    @Test
    void shouldRejectIncompleteReplayLineage() {
        UUID derivedRecordId =
            UUID.fromString(
                "80000000-0000-0000-0000-000000000903"
            );

        assertThatThrownBy(
            () ->
                new KafkaDeadLetterRecord(
                    derivedRecordId,
                    "wallet.transfer.completed.dlt",
                    0,
                    30L,
                    "wallet.transfer.completed",
                    0,
                    10L,
                    "payflow-transfer-completed-audit-v1",
                    "transaction-id",
                    "{}",
                    "IllegalStateException",
                    null,
                    KafkaDeadLetterRecordStatus.RECEIVED,
                    0,
                    RECEIVED_AT,
                    null,
                    null,
                    null,
                    null,
                    RECORD_ID,
                    0
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "Initial dead-letter records must "
                    + "use their own id as "
                    + "replayOriginId."
            );

        assertThatThrownBy(
            () ->
                new KafkaDeadLetterRecord(
                    RECORD_ID,
                    "wallet.transfer.completed.dlt",
                    0,
                    30L,
                    "wallet.transfer.completed",
                    0,
                    10L,
                    "payflow-transfer-completed-audit-v1",
                    "transaction-id",
                    "{}",
                    "IllegalStateException",
                    null,
                    KafkaDeadLetterRecordStatus.RECEIVED,
                    0,
                    RECEIVED_AT,
                    null,
                    null,
                    null,
                    null,
                    RECORD_ID,
                    1
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "Replay-derived dead-letter records "
                    + "must use a different "
                    + "replayOriginId."
            );
    }

    @Test
    void shouldRejectReplayAttemptOverflow() {
        UUID derivedRecordId =
            UUID.fromString(
                "80000000-0000-0000-0000-000000000904"
            );

        assertThatThrownBy(
            () ->
                new KafkaDeadLetterRecord(
                    derivedRecordId,
                    "wallet.transfer.completed.dlt",
                    0,
                    30L,
                    "wallet.transfer.completed",
                    0,
                    10L,
                    "payflow-transfer-completed-audit-v1",
                    "transaction-id",
                    "{}",
                    "IllegalStateException",
                    null,
                    KafkaDeadLetterRecordStatus.REPLAYING,
                    1,
                    RECEIVED_AT,
                    REPLAYED_AT,
                    "replay-worker-1",
                    LEASE_UNTIL,
                    null,
                    RECORD_ID,
                    Integer.MAX_VALUE
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "Total replay attempt count "
                    + "must not overflow."
            );
    }

    @Test
    void shouldCreateValidReplayingRecord() {
        KafkaDeadLetterRecord record =
            new KafkaDeadLetterRecord(
                RECORD_ID,
                "wallet.transfer.completed.dlt",
                0,
                25L,
                "wallet.transfer.completed",
                0,
                10L,
                "payflow-transfer-completed-audit-v1",
                "transaction-id",
                "{}",
                "IllegalStateException",
                "Temporary failure.",
                KafkaDeadLetterRecordStatus.REPLAYING,
                1,
                RECEIVED_AT,
                REPLAYED_AT,
                "replay-worker-1",
                LEASE_UNTIL,
                null
            );

        assertThat(record.status())
            .isEqualTo(
                KafkaDeadLetterRecordStatus
                    .REPLAYING
            );

        assertThat(record.replayCount())
            .isEqualTo(1);

        assertThat(record.replayLeaseOwner())
            .isEqualTo(
                "replay-worker-1"
            );

        assertThat(record.replayLeaseUntil())
            .isEqualTo(LEASE_UNTIL);
    }

    @Test
    void shouldRejectNegativeKafkaMetadata() {
        assertThatThrownBy(
            () -> new KafkaDeadLetterRecord(
                RECORD_ID,
                "wallet.transfer.completed.dlt",
                -1,
                25L,
                "wallet.transfer.completed",
                0,
                10L,
                "payflow-transfer-completed-audit-v1",
                null,
                null,
                "IllegalStateException",
                null,
                KafkaDeadLetterRecordStatus.RECEIVED,
                0,
                RECEIVED_AT,
                null,
                null,
                null,
                null
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "deadLetterPartition "
                    + "must not be negative."
            );

        assertThatThrownBy(
            () -> new KafkaDeadLetterRecord(
                RECORD_ID,
                "wallet.transfer.completed.dlt",
                0,
                -1L,
                "wallet.transfer.completed",
                0,
                10L,
                "payflow-transfer-completed-audit-v1",
                null,
                null,
                "IllegalStateException",
                null,
                KafkaDeadLetterRecordStatus.RECEIVED,
                0,
                RECEIVED_AT,
                null,
                null,
                null,
                null
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "deadLetterOffset "
                    + "must not be negative."
            );
    }

    @Test
    void shouldRejectReplayTimestampWithoutAttempt() {
        assertThatThrownBy(
            () -> new KafkaDeadLetterRecord(
                RECORD_ID,
                "wallet.transfer.completed.dlt",
                0,
                25L,
                "wallet.transfer.completed",
                0,
                10L,
                "payflow-transfer-completed-audit-v1",
                null,
                null,
                "IllegalStateException",
                null,
                KafkaDeadLetterRecordStatus.RECEIVED,
                0,
                RECEIVED_AT,
                REPLAYED_AT,
                null,
                null,
                null
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "lastReplayedAt must be null "
                    + "when replayCount is zero."
            );
    }

    @Test
    void shouldRejectReceivedRecordWithReplayAttempt() {
        assertThatThrownBy(
            () -> new KafkaDeadLetterRecord(
                RECORD_ID,
                "wallet.transfer.completed.dlt",
                0,
                25L,
                "wallet.transfer.completed",
                0,
                10L,
                "payflow-transfer-completed-audit-v1",
                null,
                null,
                "IllegalStateException",
                null,
                KafkaDeadLetterRecordStatus.RECEIVED,
                1,
                RECEIVED_AT,
                REPLAYED_AT,
                null,
                null,
                null
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "RECEIVED records must have "
                    + "a zero replayCount."
            );
    }

    @Test
    void shouldRejectReplayingRecordWithoutLease() {
        assertThatThrownBy(
            () -> new KafkaDeadLetterRecord(
                RECORD_ID,
                "wallet.transfer.completed.dlt",
                0,
                25L,
                "wallet.transfer.completed",
                0,
                10L,
                "payflow-transfer-completed-audit-v1",
                null,
                null,
                "IllegalStateException",
                null,
                KafkaDeadLetterRecordStatus.REPLAYING,
                1,
                RECEIVED_AT,
                REPLAYED_AT,
                null,
                null,
                null
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "replayLeaseOwner must not be blank."
            );
    }

    private static KafkaDeadLetterRecord
    receivedRecord(
        UUID id
    ) {
        return new KafkaDeadLetterRecord(
            id,
            "wallet.transfer.completed.dlt",
            0,
            25L,
            "wallet.transfer.completed",
            0,
            10L,
            "payflow-transfer-completed-audit-v1",
            null,
            null,
            "IllegalStateException",
            "Temporary failure.",
            KafkaDeadLetterRecordStatus.RECEIVED,
            0,
            RECEIVED_AT,
            null,
            null,
            null,
            null
        );
    }
}

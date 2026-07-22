package com.nursena.payflow.eventprocessing.application.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecordStatus;
import org.junit.jupiter.api.Test;

class KafkaDeadLetterRecordSummaryTest {

    private static final UUID RECORD_ID =
        UUID.fromString(
            "637398d5-0a02-4d10-a9af-c783ef92778b"
        );

    private static final UUID REPLAY_ORIGIN_ID =
        UUID.fromString(
            "6622b3c7-582e-47df-b6f4-8397ab487add"
        );

    private static final Instant RECEIVED_AT =
        Instant.parse(
            "2026-07-22T12:00:00Z"
        );

    @Test
    void shouldCalculateTotalReplayAttempts() {
        KafkaDeadLetterRecordSummary summary =
            createSummary(
                RECORD_ID,
                2,
                3
            );

        assertThat(
            summary.totalReplayAttempts()
        ).isEqualTo(5);
    }

    @Test
    void shouldRejectNegativeReplayCount() {
        assertThatThrownBy(
            () ->
                createSummary(
                    RECORD_ID,
                    -1,
                    0
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "replayCount must not be negative"
            );
    }

    @Test
    void shouldRejectNegativeReplayAttemptBase() {
        assertThatThrownBy(
            () ->
                createSummary(
                    RECORD_ID,
                    1,
                    -1
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "replayAttemptBase must not be negative"
            );
    }

    @Test
    void shouldRejectTotalReplayAttemptOverflow() {
        assertThatThrownBy(
            () ->
                createSummary(
                    RECORD_ID,
                    1,
                    Integer.MAX_VALUE
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "total replay attempt count "
                    + "must not overflow"
            );
    }

    @Test
    void shouldRejectNullIdentifier() {
        assertThatThrownBy(
            () ->
                createSummary(
                    null,
                    1,
                    0
                )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "id must not be null"
            );
    }

    @Test
    void shouldRejectLastReplayTimeBeforeReceivedTime() {
        assertThatThrownBy(
            () ->
                new KafkaDeadLetterRecordSummary(
                    RECORD_ID,
                    KafkaDeadLetterRecordStatus
                        .REPLAY_FAILED,
                    "wallet.transfer.completed.dlt",
                    0,
                    42L,
                    "wallet.transfer.completed",
                    0,
                    41L,
                    "payflow-transfer-consumer",
                    "java.lang.IllegalStateException",
                    1,
                    0,
                    RECEIVED_AT,
                    RECEIVED_AT.minusSeconds(1),
                    REPLAY_ORIGIN_ID,
                    true
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "lastReplayedAt must not be "
                    + "before receivedAt"
            );
    }

    private static KafkaDeadLetterRecordSummary
    createSummary(
        UUID id,
        int replayCount,
        int replayAttemptBase
    ) {
        return new KafkaDeadLetterRecordSummary(
            id,
            KafkaDeadLetterRecordStatus
                .REPLAY_FAILED,
            "wallet.transfer.completed.dlt",
            0,
            42L,
            "wallet.transfer.completed",
            0,
            41L,
            "payflow-transfer-consumer",
            "java.lang.IllegalStateException",
            replayCount,
            replayAttemptBase,
            RECEIVED_AT,
            RECEIVED_AT.plusSeconds(30),
            REPLAY_ORIGIN_ID,
            true
        );
    }
}

package com.nursena.payflow.eventprocessing.application.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecord;
import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecordStatus;
import org.junit.jupiter.api.Test;

class ClaimKafkaDeadLetterRecordResultTest {

    private static final UUID RECORD_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000001302"
        );

    @Test
    void shouldCreateClaimedResult() {
        KafkaDeadLetterRecord record =
            claimedRecord();

        ClaimKafkaDeadLetterRecordResult result =
            ClaimKafkaDeadLetterRecordResult
                .claimed(record);

        assertThat(result.isClaimed())
            .isTrue();

        assertThat(result.isNotFound())
            .isFalse();

        assertThat(result.isNotClaimable())
            .isFalse();

        assertThat(result.outcome())
            .isEqualTo(
                ClaimKafkaDeadLetterRecordResult
                    .Outcome.CLAIMED
            );

        assertThat(result.record())
            .isSameAs(record);
    }

    @Test
    void shouldCreateNotFoundResult() {
        ClaimKafkaDeadLetterRecordResult result =
            ClaimKafkaDeadLetterRecordResult
                .notFound();

        assertThat(result.isNotFound())
            .isTrue();

        assertThat(result.isClaimed())
            .isFalse();

        assertThat(result.isNotClaimable())
            .isFalse();

        assertThat(result.outcome())
            .isEqualTo(
                ClaimKafkaDeadLetterRecordResult
                    .Outcome.NOT_FOUND
            );

        assertThat(result.record())
            .isNull();
    }

    @Test
    void shouldCreateNotClaimableResult() {
        ClaimKafkaDeadLetterRecordResult result =
            ClaimKafkaDeadLetterRecordResult
                .notClaimable();

        assertThat(result.isNotClaimable())
            .isTrue();

        assertThat(result.isClaimed())
            .isFalse();

        assertThat(result.isNotFound())
            .isFalse();

        assertThat(result.outcome())
            .isEqualTo(
                ClaimKafkaDeadLetterRecordResult
                    .Outcome.NOT_CLAIMABLE
            );

        assertThat(result.record())
            .isNull();
    }

    @Test
    void shouldRejectClaimedResultWithoutRecord() {
        assertThatThrownBy(
            () ->
                new ClaimKafkaDeadLetterRecordResult(
                    ClaimKafkaDeadLetterRecordResult
                        .Outcome.CLAIMED,
                    null
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "CLAIMED result must contain "
                    + "a record."
            );
    }

    @Test
    void shouldRejectNotFoundResultWithRecord() {
        assertThatThrownBy(
            () ->
                new ClaimKafkaDeadLetterRecordResult(
                    ClaimKafkaDeadLetterRecordResult
                        .Outcome.NOT_FOUND,
                    claimedRecord()
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "NOT_FOUND result must not contain "
                    + "a record."
            );
    }

    @Test
    void shouldRejectNotClaimableResultWithRecord() {
        assertThatThrownBy(
            () ->
                new ClaimKafkaDeadLetterRecordResult(
                    ClaimKafkaDeadLetterRecordResult
                        .Outcome.NOT_CLAIMABLE,
                    claimedRecord()
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "NOT_CLAIMABLE result must not "
                    + "contain a record."
            );
    }

    @Test
    void shouldRequireOutcome() {
        assertThatThrownBy(
            () ->
                new ClaimKafkaDeadLetterRecordResult(
                    null,
                    null
                )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "outcome must not be null"
            );
    }

    private static KafkaDeadLetterRecord
    claimedRecord() {
        Instant receivedAt =
            Instant.parse(
                "2026-07-21T19:00:00Z"
            );

        Instant claimedAt =
            Instant.parse(
                "2026-07-21T19:05:00Z"
            );

        return new KafkaDeadLetterRecord(
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
            receivedAt,
            claimedAt,
            "replay-worker-1",
            claimedAt.plusSeconds(30),
            null,
            RECORD_ID,
            0
        );
    }
}

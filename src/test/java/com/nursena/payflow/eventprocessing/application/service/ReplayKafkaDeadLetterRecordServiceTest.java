package com.nursena.payflow.eventprocessing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.application.model.ClaimKafkaDeadLetterRecordCommand;
import com.nursena.payflow.eventprocessing.application.model.ClaimKafkaDeadLetterRecordResult;
import com.nursena.payflow.eventprocessing.application.model.ReplayKafkaDeadLetterRecordCommand;
import com.nursena.payflow.eventprocessing.application.model.ReplayKafkaDeadLetterRecordResult;
import com.nursena.payflow.eventprocessing.application.port.in.ClaimKafkaDeadLetterRecordUseCase;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterReplayLifecyclePort;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterReplayPublisherPort;
import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecord;
import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecordStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReplayKafkaDeadLetterRecordServiceTest {

    private static final UUID RECORD_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000002001"
        );

    private static final UUID OTHER_RECORD_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000002002"
        );

    private static final UUID REPLAY_ORIGIN_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000002000"
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

    private static final Instant NOW =
        Instant.parse(
            "2026-07-21T20:06:00Z"
        );

    private static final ReplayKafkaDeadLetterRecordCommand
        COMMAND =
        new ReplayKafkaDeadLetterRecordCommand(
            RECORD_ID
        );

    @Mock
    private ClaimKafkaDeadLetterRecordUseCase
        claimUseCase;

    @Mock
    private KafkaDeadLetterReplayPublisherPort
        publisherPort;

    @Mock
    private KafkaDeadLetterReplayLifecyclePort
        lifecyclePort;

    private ReplayKafkaDeadLetterRecordService
        service;

    @BeforeEach
    void setUp() {
        service =
            new ReplayKafkaDeadLetterRecordService(
                claimUseCase,
                publisherPort,
                lifecyclePort,
                Clock.fixed(
                    NOW,
                    ZoneOffset.UTC
                )
            );
    }

    @Test
    void shouldReturnNotClaimableWithoutPublishing() {
        when(claimUseCase.claim(
            new ClaimKafkaDeadLetterRecordCommand(
                RECORD_ID
            )
        )).thenReturn(
            ClaimKafkaDeadLetterRecordResult
                .notClaimable()
        );

        ReplayKafkaDeadLetterRecordResult result =
            service.replay(COMMAND);

        assertThat(result.isNotClaimable())
            .isTrue();

        verifyNoInteractions(
            publisherPort,
            lifecyclePort
        );
    }

    @Test
    void shouldPublishAndPersistReplayedOutcome() {
        KafkaDeadLetterRecord record =
            replayingRecord(RECORD_ID);

        when(claimUseCase.claim(
            new ClaimKafkaDeadLetterRecordCommand(
                RECORD_ID
            )
        )).thenReturn(
            ClaimKafkaDeadLetterRecordResult
                .claimed(record)
        );

        when(lifecyclePort.tryMarkReplayed(
            RECORD_ID,
            WORKER_ID,
            NOW
        )).thenReturn(true);

        ReplayKafkaDeadLetterRecordResult result =
            service.replay(COMMAND);

        assertThat(result.isReplayed())
            .isTrue();

        InOrder ordered =
            inOrder(
                claimUseCase,
                publisherPort,
                lifecyclePort
            );

        ordered.verify(claimUseCase)
            .claim(
                new ClaimKafkaDeadLetterRecordCommand(
                    RECORD_ID
                )
            );

        ordered.verify(publisherPort)
            .publish(record);

        ordered.verify(lifecyclePort)
            .tryMarkReplayed(
                RECORD_ID,
                WORKER_ID,
                NOW
            );

        verify(lifecyclePort, never())
            .tryMarkReplayFailed(
                eq(RECORD_ID),
                eq(WORKER_ID),
                eq(NOW),
                anyString()
            );
    }

    @Test
    void shouldPersistReplayFailureWhenPublishingFails() {
        KafkaDeadLetterRecord record =
            replayingRecord(RECORD_ID);

        when(claimUseCase.claim(
            new ClaimKafkaDeadLetterRecordCommand(
                RECORD_ID
            )
        )).thenReturn(
            ClaimKafkaDeadLetterRecordResult
                .claimed(record)
        );

        doThrow(
            new IllegalStateException(
                "Broker is unavailable."
            )
        )
            .when(publisherPort)
            .publish(record);

        when(lifecyclePort.tryMarkReplayFailed(
            RECORD_ID,
            WORKER_ID,
            NOW,
            "IllegalStateException: "
                + "Broker is unavailable."
        )).thenReturn(true);

        ReplayKafkaDeadLetterRecordResult result =
            service.replay(COMMAND);

        assertThat(result.isReplayFailed())
            .isTrue();

        verify(lifecyclePort)
            .tryMarkReplayFailed(
                RECORD_ID,
                WORKER_ID,
                NOW,
                "IllegalStateException: "
                    + "Broker is unavailable."
            );

        verify(lifecyclePort, never())
            .tryMarkReplayed(
                RECORD_ID,
                WORKER_ID,
                NOW
            );
    }

    @Test
    void shouldReturnUnresolvedWhenReplayedTransitionIsRejected() {
        KafkaDeadLetterRecord record =
            replayingRecord(RECORD_ID);

        when(claimUseCase.claim(
            new ClaimKafkaDeadLetterRecordCommand(
                RECORD_ID
            )
        )).thenReturn(
            ClaimKafkaDeadLetterRecordResult
                .claimed(record)
        );

        when(lifecyclePort.tryMarkReplayed(
            RECORD_ID,
            WORKER_ID,
            NOW
        )).thenReturn(false);

        ReplayKafkaDeadLetterRecordResult result =
            service.replay(COMMAND);

        assertThat(result.isUnresolved())
            .isTrue();

        verify(publisherPort)
            .publish(record);
    }

    @Test
    void shouldReturnUnresolvedWhenFailureTransitionIsRejected() {
        KafkaDeadLetterRecord record =
            replayingRecord(RECORD_ID);

        when(claimUseCase.claim(
            new ClaimKafkaDeadLetterRecordCommand(
                RECORD_ID
            )
        )).thenReturn(
            ClaimKafkaDeadLetterRecordResult
                .claimed(record)
        );

        doThrow(
            new IllegalStateException(
                "Broker is unavailable."
            )
        )
            .when(publisherPort)
            .publish(record);

        when(lifecyclePort.tryMarkReplayFailed(
            RECORD_ID,
            WORKER_ID,
            NOW,
            "IllegalStateException: "
                + "Broker is unavailable."
        )).thenReturn(false);

        ReplayKafkaDeadLetterRecordResult result =
            service.replay(COMMAND);

        assertThat(result.isUnresolved())
            .isTrue();
    }

    @Test
    void shouldReturnUnresolvedWhenLifecyclePersistenceFails() {
        KafkaDeadLetterRecord record =
            replayingRecord(RECORD_ID);

        when(claimUseCase.claim(
            new ClaimKafkaDeadLetterRecordCommand(
                RECORD_ID
            )
        )).thenReturn(
            ClaimKafkaDeadLetterRecordResult
                .claimed(record)
        );

        when(lifecyclePort.tryMarkReplayed(
            RECORD_ID,
            WORKER_ID,
            NOW
        )).thenThrow(
            new IllegalStateException(
                "Database is unavailable."
            )
        );

        ReplayKafkaDeadLetterRecordResult result =
            service.replay(COMMAND);

        assertThat(result.isUnresolved())
            .isTrue();

        verify(publisherPort)
            .publish(record);
    }

    @Test
    void shouldTruncatePersistedPublishingFailure() {
        KafkaDeadLetterRecord record =
            replayingRecord(RECORD_ID);

        String longMessage =
            "x".repeat(2_000);

        when(claimUseCase.claim(
            new ClaimKafkaDeadLetterRecordCommand(
                RECORD_ID
            )
        )).thenReturn(
            ClaimKafkaDeadLetterRecordResult
                .claimed(record)
        );

        doThrow(
            new IllegalStateException(
                longMessage
            )
        )
            .when(publisherPort)
            .publish(record);

        when(lifecyclePort.tryMarkReplayFailed(
            eq(RECORD_ID),
            eq(WORKER_ID),
            eq(NOW),
            anyString()
        )).thenReturn(true);

        ReplayKafkaDeadLetterRecordResult result =
            service.replay(COMMAND);

        assertThat(result.isReplayFailed())
            .isTrue();

        ArgumentCaptor<String> errorCaptor =
            ArgumentCaptor.forClass(
                String.class
            );

        verify(lifecyclePort)
            .tryMarkReplayFailed(
                eq(RECORD_ID),
                eq(WORKER_ID),
                eq(NOW),
                errorCaptor.capture()
            );

        assertThat(errorCaptor.getValue())
            .hasSize(1_000)
            .startsWith(
                "IllegalStateException: "
            );
    }

    @Test
    void shouldRejectClaimedRecordWithDifferentIdentifier() {
        KafkaDeadLetterRecord record =
            replayingRecord(
                OTHER_RECORD_ID
            );

        when(claimUseCase.claim(
            new ClaimKafkaDeadLetterRecordCommand(
                RECORD_ID
            )
        )).thenReturn(
            ClaimKafkaDeadLetterRecordResult
                .claimed(record)
        );

        assertThatThrownBy(() ->
            service.replay(COMMAND)
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessage(
                "Claimed Kafka dead-letter "
                    + "record identifier does not "
                    + "match the replay command."
            );

        verifyNoInteractions(
            publisherPort,
            lifecyclePort
        );
    }

    @Test
    void shouldRejectClaimedRecordThatIsNotReplaying() {
        KafkaDeadLetterRecord record =
            receivedRecord();

        when(claimUseCase.claim(
            new ClaimKafkaDeadLetterRecordCommand(
                RECORD_ID
            )
        )).thenReturn(
            ClaimKafkaDeadLetterRecordResult
                .claimed(record)
        );

        assertThatThrownBy(() ->
            service.replay(COMMAND)
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessage(
                "Claimed Kafka dead-letter "
                    + "record must be REPLAYING."
            );

        verifyNoInteractions(
            publisherPort,
            lifecyclePort
        );
    }

    @Test
    void shouldRequireCommand() {
        assertThatThrownBy(() ->
            service.replay(null)
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "command must not be null"
            );

        verifyNoInteractions(
            claimUseCase,
            publisherPort,
            lifecyclePort
        );
    }

    private static KafkaDeadLetterRecord
    replayingRecord(
        UUID recordId
    ) {
        return new KafkaDeadLetterRecord(
            recordId,
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
                "80000000-0000-0000-0000-000000002003"
            }
            """,
            "java.lang.IllegalStateException",
            "Temporary processing failure.",
            KafkaDeadLetterRecordStatus.REPLAYING,
            3,
            RECEIVED_AT,
            CLAIMED_AT,
            WORKER_ID,
            CLAIMED_AT.plusSeconds(30),
            null,
            REPLAY_ORIGIN_ID,
            2
        );
    }

    private static KafkaDeadLetterRecord
    receivedRecord() {
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
            """
            {
              "eventId":
                "80000000-0000-0000-0000-000000002003"
            }
            """,
            "java.lang.IllegalStateException",
            "Temporary processing failure.",
            KafkaDeadLetterRecordStatus.RECEIVED,
            0,
            RECEIVED_AT,
            null,
            null,
            null,
            null,
            RECORD_ID,
            0
        );
    }
}

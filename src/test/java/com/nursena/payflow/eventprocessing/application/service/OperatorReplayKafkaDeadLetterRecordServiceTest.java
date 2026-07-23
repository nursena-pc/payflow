package com.nursena.payflow.eventprocessing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterCommandAudit;
import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterCommandAuditOutcome;
import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterCommandAuditStage;
import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterCommandType;
import com.nursena.payflow.eventprocessing.application.model.OperatorReplayKafkaDeadLetterRecordCommand;
import com.nursena.payflow.eventprocessing.application.model.ReplayKafkaDeadLetterRecordCommand;
import com.nursena.payflow.eventprocessing.application.model.ReplayKafkaDeadLetterRecordResult;
import com.nursena.payflow.eventprocessing.application.port.in.ReplayKafkaDeadLetterRecordUseCase;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterCommandAuditPort;
import com.nursena.payflow.eventprocessing.domain.exception.KafkaDeadLetterCommandAuditException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OperatorReplayKafkaDeadLetterRecordServiceTest {

    private static final UUID OPERATOR_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000003001"
        );

    private static final UUID RECORD_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000003002"
        );

    private static final UUID COMMAND_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000003003"
        );

    private static final UUID ATTEMPT_AUDIT_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000003004"
        );

    private static final UUID COMPLETION_AUDIT_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000003005"
        );

    private static final Instant NOW =
        Instant.parse(
            "2026-07-23T14:30:00.123456789Z"
        );

    private static final Instant EXPECTED_TIME =
        Instant.parse(
            "2026-07-23T14:30:00.123456Z"
        );

    private static final OperatorReplayKafkaDeadLetterRecordCommand
        COMMAND =
        new OperatorReplayKafkaDeadLetterRecordCommand(
            OPERATOR_ID,
            RECORD_ID
        );

    @Mock
    private ReplayKafkaDeadLetterRecordUseCase
        replayUseCase;

    @Mock
    private KafkaDeadLetterCommandAuditPort
        auditPort;

    private OperatorReplayKafkaDeadLetterRecordService
        service;

    @BeforeEach
    void setUp() {
        service = newService(idSupplier());
    }

    @ParameterizedTest
    @MethodSource("replayOutcomes")
    void shouldAuditReplayOutcome(
        ReplayKafkaDeadLetterRecordResult delegateResult,
        KafkaDeadLetterCommandAuditOutcome
            expectedOutcome
    ) {
        when(
            replayUseCase.replay(
                new ReplayKafkaDeadLetterRecordCommand(
                    RECORD_ID
                )
            )
        ).thenReturn(delegateResult);

        ReplayKafkaDeadLetterRecordResult result =
            service.replay(COMMAND);

        assertThat(result)
            .isSameAs(delegateResult);

        ArgumentCaptor<KafkaDeadLetterCommandAudit>
            auditCaptor =
            ArgumentCaptor.forClass(
                KafkaDeadLetterCommandAudit.class
            );

        verify(auditPort, times(2))
            .append(auditCaptor.capture());

        List<KafkaDeadLetterCommandAudit> audits =
            auditCaptor.getAllValues();

        assertAttempted(audits.get(0));
        assertCompleted(
            audits.get(1),
            expectedOutcome
        );

        InOrder ordered =
            inOrder(
                auditPort,
                replayUseCase
            );

        ordered.verify(auditPort)
            .append(any());
        ordered.verify(replayUseCase)
            .replay(
                new ReplayKafkaDeadLetterRecordCommand(
                    RECORD_ID
                )
            );
        ordered.verify(auditPort)
            .append(any());
    }

    @Test
    void shouldFailClosedWhenAttemptCannotBePersisted() {
        IllegalStateException persistenceFailure =
            new IllegalStateException(
                "database detail"
            );

        doThrow(persistenceFailure)
            .when(auditPort)
            .append(any());

        assertThatThrownBy(
            () -> service.replay(COMMAND)
        )
            .isInstanceOf(
                KafkaDeadLetterCommandAuditException
                    .class
            )
            .hasMessage(
                "Kafka dead-letter command audit "
                    + "attempt could not be persisted."
            )
            .hasCause(persistenceFailure)
            .satisfies(exception ->
                assertThat(
                    ((KafkaDeadLetterCommandAuditException)
                        exception).getReason()
                ).isEqualTo(
                    KafkaDeadLetterCommandAuditException
                        .Reason
                        .ATTEMPT_PERSISTENCE_FAILED
                )
            );

        verify(auditPort)
            .append(any());
        verifyNoInteractions(replayUseCase);
    }

    @Test
    void shouldSurfaceAmbiguousCompletionWhenFinalAuditFails() {
        ReplayKafkaDeadLetterRecordResult delegateResult =
            ReplayKafkaDeadLetterRecordResult
                .replayed();

        when(replayUseCase.replay(any()))
            .thenReturn(delegateResult);

        IllegalStateException persistenceFailure =
            new IllegalStateException(
                "database detail"
            );

        doNothing()
            .doThrow(persistenceFailure)
            .when(auditPort)
            .append(any());

        assertThatThrownBy(
            () -> service.replay(COMMAND)
        )
            .isInstanceOf(
                KafkaDeadLetterCommandAuditException
                    .class
            )
            .hasMessage(
                "Kafka dead-letter command completion "
                    + "could not be audited safely."
            )
            .hasCause(persistenceFailure)
            .satisfies(exception ->
                assertThat(
                    ((KafkaDeadLetterCommandAuditException)
                        exception).getReason()
                ).isEqualTo(
                    KafkaDeadLetterCommandAuditException
                        .Reason
                        .COMPLETION_PERSISTENCE_FAILED
                )
            );

        verify(replayUseCase)
            .replay(
                new ReplayKafkaDeadLetterRecordCommand(
                    RECORD_ID
                )
            );
        verify(auditPort, times(2))
            .append(any());
    }

    @Test
    void shouldAuditUnexpectedReplayFailureWithoutDetails() {
        IllegalStateException commandFailure =
            new IllegalStateException(
                "payload and broker detail"
            );

        when(replayUseCase.replay(any()))
            .thenThrow(commandFailure);

        assertThatThrownBy(
            () -> service.replay(COMMAND)
        )
            .isInstanceOf(
                KafkaDeadLetterCommandAuditException
                    .class
            )
            .hasMessage(
                "Kafka dead-letter command failed "
                    + "unexpectedly."
            )
            .hasCause(commandFailure)
            .satisfies(exception ->
                assertThat(
                    ((KafkaDeadLetterCommandAuditException)
                        exception).getReason()
                ).isEqualTo(
                    KafkaDeadLetterCommandAuditException
                        .Reason
                        .COMMAND_INTERNAL_FAILURE
                )
            );

        ArgumentCaptor<KafkaDeadLetterCommandAudit>
            auditCaptor =
            ArgumentCaptor.forClass(
                KafkaDeadLetterCommandAudit.class
            );

        verify(auditPort, times(2))
            .append(auditCaptor.capture());

        KafkaDeadLetterCommandAudit completed =
            auditCaptor.getAllValues().get(1);

        assertCompleted(
            completed,
            KafkaDeadLetterCommandAuditOutcome
                .INTERNAL_FAILURE
        );
        assertThat(completed.errorCode())
            .isEqualTo(
                "KAFKA_DEAD_LETTER_COMMAND_INTERNAL_FAILURE"
            )
            .doesNotContain(
                commandFailure.getMessage()
            );
    }

    @Test
    void shouldPreferCompletionAuditFailureWhenInternalFailureCannotBeAudited() {
        IllegalStateException commandFailure =
            new IllegalStateException(
                "command detail"
            );

        when(replayUseCase.replay(any()))
            .thenThrow(commandFailure);

        IllegalStateException auditFailure =
            new IllegalStateException(
                "audit detail"
            );

        doNothing()
            .doThrow(auditFailure)
            .when(auditPort)
            .append(any());

        assertThatThrownBy(
            () -> service.replay(COMMAND)
        )
            .isInstanceOf(
                KafkaDeadLetterCommandAuditException
                    .class
            )
            .hasCause(auditFailure)
            .satisfies(exception -> {
                KafkaDeadLetterCommandAuditException
                    auditException =
                    (KafkaDeadLetterCommandAuditException)
                        exception;

                assertThat(auditException.getReason())
                    .isEqualTo(
                        KafkaDeadLetterCommandAuditException
                            .Reason
                            .COMPLETION_PERSISTENCE_FAILED
                    );
                assertThat(
                    auditException.getSuppressed()
                ).containsExactly(commandFailure);
            });
    }

    @Test
    void shouldTreatNullDelegateResultAsInternalFailure() {
        when(replayUseCase.replay(any()))
            .thenReturn(null);

        assertThatThrownBy(
            () -> service.replay(COMMAND)
        )
            .isInstanceOf(
                KafkaDeadLetterCommandAuditException
                    .class
            )
            .hasCauseInstanceOf(
                NullPointerException.class
            )
            .satisfies(exception ->
                assertThat(
                    ((KafkaDeadLetterCommandAuditException)
                        exception).getReason()
                ).isEqualTo(
                    KafkaDeadLetterCommandAuditException
                        .Reason
                        .COMMAND_INTERNAL_FAILURE
                )
            );

        verify(auditPort, times(2))
            .append(any());
    }

    @Test
    void shouldRequireCommand() {
        assertThatThrownBy(
            () -> service.replay(null)
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "command must not be null"
            );

        verifyNoInteractions(
            auditPort,
            replayUseCase
        );
    }

    @Test
    void shouldRequireReplayUseCase() {
        assertThatThrownBy(
            () -> new OperatorReplayKafkaDeadLetterRecordService(
                null,
                auditPort,
                fixedClock(),
                idSupplier()
            )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "replayUseCase must not be null"
            );
    }

    @Test
    void shouldFailClosedWhenIdentifierSupplierReturnsNull() {
        OperatorReplayKafkaDeadLetterRecordService
            invalidService =
            newService(() -> null);

        assertThatThrownBy(
            () -> invalidService.replay(COMMAND)
        )
            .isInstanceOf(
                KafkaDeadLetterCommandAuditException
                    .class
            )
            .hasCauseInstanceOf(
                NullPointerException.class
            )
            .satisfies(exception ->
                assertThat(
                    ((KafkaDeadLetterCommandAuditException)
                        exception).getReason()
                ).isEqualTo(
                    KafkaDeadLetterCommandAuditException
                        .Reason
                        .ATTEMPT_PERSISTENCE_FAILED
                )
            );

        verify(auditPort, never())
            .append(any());
        verifyNoInteractions(replayUseCase);
    }

    private OperatorReplayKafkaDeadLetterRecordService
    newService(Supplier<UUID> supplier) {
        return new
            OperatorReplayKafkaDeadLetterRecordService(
            replayUseCase,
            auditPort,
            fixedClock(),
            supplier
        );
    }

    private static Clock fixedClock() {
        return Clock.fixed(
            NOW,
            ZoneOffset.UTC
        );
    }

    private static Supplier<UUID> idSupplier() {
        Queue<UUID> identifiers =
            new ArrayDeque<>(
                List.of(
                    COMMAND_ID,
                    ATTEMPT_AUDIT_ID,
                    COMPLETION_AUDIT_ID
                )
            );

        return identifiers::remove;
    }

    private static void assertAttempted(
        KafkaDeadLetterCommandAudit audit
    ) {
        assertThat(audit.id())
            .isEqualTo(ATTEMPT_AUDIT_ID);
        assertThat(audit.commandId())
            .isEqualTo(COMMAND_ID);
        assertThat(audit.stage())
            .isEqualTo(
                KafkaDeadLetterCommandAuditStage
                    .ATTEMPTED
            );
        assertCommonAuditFields(audit);
        assertThat(audit.outcome())
            .isNull();
        assertThat(audit.errorCode())
            .isNull();
    }

    private static void assertCompleted(
        KafkaDeadLetterCommandAudit audit,
        KafkaDeadLetterCommandAuditOutcome outcome
    ) {
        assertThat(audit.id())
            .isEqualTo(COMPLETION_AUDIT_ID);
        assertThat(audit.commandId())
            .isEqualTo(COMMAND_ID);
        assertThat(audit.stage())
            .isEqualTo(
                KafkaDeadLetterCommandAuditStage
                    .COMPLETED
            );
        assertCommonAuditFields(audit);
        assertThat(audit.outcome())
            .isEqualTo(outcome);
        assertThat(audit.errorCode())
            .isEqualTo(outcome.safeErrorCode());
    }

    private static void assertCommonAuditFields(
        KafkaDeadLetterCommandAudit audit
    ) {
        assertThat(audit.operatorId())
            .isEqualTo(OPERATOR_ID);
        assertThat(audit.deadLetterRecordId())
            .isEqualTo(RECORD_ID);
        assertThat(audit.commandType())
            .isEqualTo(
                KafkaDeadLetterCommandType.REPLAY
            );
        assertThat(audit.occurredAt())
            .isEqualTo(EXPECTED_TIME);
    }

    private static Stream<Arguments> replayOutcomes() {
        return Stream.of(
            Arguments.of(
                ReplayKafkaDeadLetterRecordResult
                    .replayed(),
                KafkaDeadLetterCommandAuditOutcome
                    .REPLAYED
            ),
            Arguments.of(
                ReplayKafkaDeadLetterRecordResult
                    .notFound(),
                KafkaDeadLetterCommandAuditOutcome
                    .REPLAY_NOT_FOUND
            ),
            Arguments.of(
                ReplayKafkaDeadLetterRecordResult
                    .notClaimable(),
                KafkaDeadLetterCommandAuditOutcome
                    .REPLAY_NOT_CLAIMABLE
            ),
            Arguments.of(
                ReplayKafkaDeadLetterRecordResult
                    .replayFailed(),
                KafkaDeadLetterCommandAuditOutcome
                    .REPLAY_FAILED
            ),
            Arguments.of(
                ReplayKafkaDeadLetterRecordResult
                    .unresolved(),
                KafkaDeadLetterCommandAuditOutcome
                    .REPLAY_UNRESOLVED
            )
        );
    }
}

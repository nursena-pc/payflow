package com.nursena.payflow.eventprocessing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
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

import com.nursena.payflow.eventprocessing.application.model.DiscardKafkaDeadLetterRecordCommand;
import com.nursena.payflow.eventprocessing.application.model.DiscardKafkaDeadLetterRecordResult;
import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterCommandAudit;
import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterCommandAuditOutcome;
import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterCommandAuditStage;
import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterCommandType;
import com.nursena.payflow.eventprocessing.application.model.OperatorDiscardKafkaDeadLetterRecordCommand;
import com.nursena.payflow.eventprocessing.application.port.in.DiscardKafkaDeadLetterRecordUseCase;
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
class OperatorDiscardKafkaDeadLetterRecordServiceTest {

    private static final UUID OPERATOR_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000003101"
        );

    private static final UUID RECORD_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000003102"
        );

    private static final UUID COMMAND_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000003103"
        );

    private static final UUID ATTEMPT_AUDIT_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000003104"
        );

    private static final UUID COMPLETION_AUDIT_ID =
        UUID.fromString(
            "80000000-0000-0000-0000-000000003105"
        );

    private static final Instant NOW =
        Instant.parse(
            "2026-07-23T14:35:00.987654321Z"
        );

    private static final Instant EXPECTED_TIME =
        Instant.parse(
            "2026-07-23T14:35:00.987654Z"
        );

    private static final OperatorDiscardKafkaDeadLetterRecordCommand
        COMMAND =
        new OperatorDiscardKafkaDeadLetterRecordCommand(
            OPERATOR_ID,
            RECORD_ID
        );

    @Mock
    private DiscardKafkaDeadLetterRecordUseCase
        discardUseCase;

    @Mock
    private KafkaDeadLetterCommandAuditPort
        auditPort;

    private OperatorDiscardKafkaDeadLetterRecordService
        service;

    @BeforeEach
    void setUp() {
        service = newService(idSupplier());
    }

    @ParameterizedTest
    @MethodSource("discardOutcomes")
    void shouldAuditDiscardOutcome(
        DiscardKafkaDeadLetterRecordResult delegateResult,
        KafkaDeadLetterCommandAuditOutcome
            expectedOutcome
    ) {
        when(
            discardUseCase.discard(
                new DiscardKafkaDeadLetterRecordCommand(
                    RECORD_ID
                )
            )
        ).thenReturn(delegateResult);

        DiscardKafkaDeadLetterRecordResult result =
            service.discard(COMMAND);

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
                discardUseCase
            );

        ordered.verify(auditPort)
            .append(any());
        ordered.verify(discardUseCase)
            .discard(
                new DiscardKafkaDeadLetterRecordCommand(
                    RECORD_ID
                )
            );
        ordered.verify(auditPort)
            .append(any());
    }

    @Test
    void shouldAuditUnexpectedDiscardFailure() {
        IllegalStateException commandFailure =
            new IllegalStateException(
                "sensitive database detail"
            );

        when(discardUseCase.discard(any()))
            .thenThrow(commandFailure);

        assertThatThrownBy(
            () -> service.discard(COMMAND)
        )
            .isInstanceOf(
                KafkaDeadLetterCommandAuditException
                    .class
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
            .doesNotContain(
                commandFailure.getMessage()
            );
    }

    @Test
    void shouldFailClosedBeforeDiscardWhenAttemptFails() {
        IllegalStateException persistenceFailure =
            new IllegalStateException(
                "database detail"
            );

        doThrow(persistenceFailure)
            .when(auditPort)
            .append(any());

        assertThatThrownBy(
            () -> service.discard(COMMAND)
        )
            .isInstanceOf(
                KafkaDeadLetterCommandAuditException
                    .class
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

        verifyNoInteractions(discardUseCase);
    }

    @Test
    void shouldRequireCommand() {
        assertThatThrownBy(
            () -> service.discard(null)
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "command must not be null"
            );

        verifyNoInteractions(
            auditPort,
            discardUseCase
        );
    }

    @Test
    void shouldRequireDiscardUseCase() {
        assertThatThrownBy(
            () -> new OperatorDiscardKafkaDeadLetterRecordService(
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
                "discardUseCase must not be null"
            );
    }

    private OperatorDiscardKafkaDeadLetterRecordService
    newService(Supplier<UUID> supplier) {
        return new
            OperatorDiscardKafkaDeadLetterRecordService(
            discardUseCase,
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
                KafkaDeadLetterCommandType.DISCARD
            );
        assertThat(audit.occurredAt())
            .isEqualTo(EXPECTED_TIME);
    }

    private static Stream<Arguments> discardOutcomes() {
        return Stream.of(
            Arguments.of(
                DiscardKafkaDeadLetterRecordResult
                    .discarded(),
                KafkaDeadLetterCommandAuditOutcome
                    .DISCARDED
            ),
            Arguments.of(
                DiscardKafkaDeadLetterRecordResult
                    .alreadyDiscarded(),
                KafkaDeadLetterCommandAuditOutcome
                    .ALREADY_DISCARDED
            ),
            Arguments.of(
                DiscardKafkaDeadLetterRecordResult
                    .notFound(),
                KafkaDeadLetterCommandAuditOutcome
                    .DISCARD_NOT_FOUND
            ),
            Arguments.of(
                DiscardKafkaDeadLetterRecordResult
                    .notDiscardable(),
                KafkaDeadLetterCommandAuditOutcome
                    .DISCARD_NOT_DISCARDABLE
            )
        );
    }
}

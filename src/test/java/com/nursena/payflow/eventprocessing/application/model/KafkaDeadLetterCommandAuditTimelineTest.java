package com.nursena.payflow.eventprocessing.application.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class KafkaDeadLetterCommandAuditTimelineTest {

    private static final UUID COMMAND_ID =
        UUID.fromString(
            "b2fdb860-df65-4c43-ab69-87f930dd16dc"
        );

    private static final UUID OPERATOR_ID =
        UUID.fromString(
            "152468c4-eeba-4a17-b19c-dd0fd4ca63a7"
        );

    private static final UUID RECORD_ID =
        UUID.fromString(
            "9f9085f8-a4bf-412d-bc3b-9c0de54ca383"
        );

    @Test
    void shouldRepresentIncompleteTimeline() {
        KafkaDeadLetterCommandAudit attempted =
            createAttemptedAudit(
                COMMAND_ID,
                OPERATOR_ID,
                RECORD_ID
            );

        KafkaDeadLetterCommandAuditTimeline timeline =
            new KafkaDeadLetterCommandAuditTimeline(
                COMMAND_ID,
                List.of(attempted)
            );

        assertThat(timeline.commandId())
            .isEqualTo(COMMAND_ID);
        assertThat(timeline.entries())
            .containsExactly(attempted);
        assertThat(timeline.complete()).isFalse();
    }

    @Test
    void shouldRepresentCompleteTimeline() {
        KafkaDeadLetterCommandAudit attempted =
            createAttemptedAudit(
                COMMAND_ID,
                OPERATOR_ID,
                RECORD_ID
            );
        KafkaDeadLetterCommandAudit completed =
            createCompletedAudit(
                COMMAND_ID,
                OPERATOR_ID,
                RECORD_ID,
                Instant.parse(
                    "2026-07-25T10:00:01Z"
                )
            );

        KafkaDeadLetterCommandAuditTimeline timeline =
            new KafkaDeadLetterCommandAuditTimeline(
                COMMAND_ID,
                List.of(
                    attempted,
                    completed
                )
            );

        assertThat(timeline.entries())
            .containsExactly(
                attempted,
                completed
            );
        assertThat(timeline.complete()).isTrue();
    }

    @Test
    void shouldCreateImmutableCopyOfEntries() {
        List<KafkaDeadLetterCommandAudit> source =
            new ArrayList<>();

        source.add(
            createAttemptedAudit(
                COMMAND_ID,
                OPERATOR_ID,
                RECORD_ID
            )
        );

        KafkaDeadLetterCommandAuditTimeline timeline =
            new KafkaDeadLetterCommandAuditTimeline(
                COMMAND_ID,
                source
            );

        source.clear();

        assertThat(timeline.entries()).hasSize(1);
        assertThatThrownBy(
            () -> timeline.entries().clear()
        )
            .isInstanceOf(
                UnsupportedOperationException.class
            );
    }

    @Test
    void shouldRejectEmptyTimeline() {
        assertThatThrownBy(
            () ->
                new KafkaDeadLetterCommandAuditTimeline(
                    COMMAND_ID,
                    List.of()
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "entries must not be empty"
            );
    }

    @Test
    void shouldRejectTimelineStartingWithCompleted() {
        KafkaDeadLetterCommandAudit completed =
            createCompletedAudit(
                COMMAND_ID,
                OPERATOR_ID,
                RECORD_ID,
                Instant.parse(
                    "2026-07-25T10:00:01Z"
                )
            );

        assertThatThrownBy(
            () ->
                new KafkaDeadLetterCommandAuditTimeline(
                    COMMAND_ID,
                    List.of(completed)
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "timeline must start with ATTEMPTED"
            );
    }

    @Test
    void shouldRejectMismatchedCommandIdentifier() {
        KafkaDeadLetterCommandAudit attempted =
            createAttemptedAudit(
                UUID.fromString(
                    "845c78c5-d0a2-40fb-9475-43891971696d"
                ),
                OPERATOR_ID,
                RECORD_ID
            );

        assertThatThrownBy(
            () ->
                new KafkaDeadLetterCommandAuditTimeline(
                    COMMAND_ID,
                    List.of(attempted)
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "entry commandId must match "
                    + "timeline commandId"
            );
    }

    @Test
    void shouldRejectMismatchedOperatorIdentifier() {
        KafkaDeadLetterCommandAudit attempted =
            createAttemptedAudit(
                COMMAND_ID,
                OPERATOR_ID,
                RECORD_ID
            );
        KafkaDeadLetterCommandAudit completed =
            createCompletedAudit(
                COMMAND_ID,
                UUID.fromString(
                    "4c14e9cd-260d-4c72-b994-4ac6259a9be0"
                ),
                RECORD_ID,
                Instant.parse(
                    "2026-07-25T10:00:01Z"
                )
            );

        assertThatThrownBy(
            () ->
                new KafkaDeadLetterCommandAuditTimeline(
                    COMMAND_ID,
                    List.of(
                        attempted,
                        completed
                    )
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "timeline operatorId values must match"
            );
    }

    @Test
    void shouldRejectCompletionBeforeAttempt() {
        KafkaDeadLetterCommandAudit attempted =
            createAttemptedAudit(
                COMMAND_ID,
                OPERATOR_ID,
                RECORD_ID
            );
        KafkaDeadLetterCommandAudit completed =
            createCompletedAudit(
                COMMAND_ID,
                OPERATOR_ID,
                RECORD_ID,
                Instant.parse(
                    "2026-07-25T09:59:59Z"
                )
            );

        assertThatThrownBy(
            () ->
                new KafkaDeadLetterCommandAuditTimeline(
                    COMMAND_ID,
                    List.of(
                        attempted,
                        completed
                    )
                )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "COMPLETED must not occur "
                    + "before ATTEMPTED"
            );
    }

    private static KafkaDeadLetterCommandAudit
    createAttemptedAudit(
        UUID commandId,
        UUID operatorId,
        UUID recordId
    ) {
        return KafkaDeadLetterCommandAudit
            .attempted(
                UUID.fromString(
                    "45a97a7c-291b-4392-8b14-2d9d3df813a2"
                ),
                commandId,
                operatorId,
                recordId,
                KafkaDeadLetterCommandType.REPLAY,
                Instant.parse(
                    "2026-07-25T10:00:00Z"
                )
            );
    }

    private static KafkaDeadLetterCommandAudit
    createCompletedAudit(
        UUID commandId,
        UUID operatorId,
        UUID recordId,
        Instant occurredAt
    ) {
        return KafkaDeadLetterCommandAudit
            .completed(
                UUID.fromString(
                    "40aa80d0-358c-4622-bd77-e5c2dfa5373a"
                ),
                commandId,
                operatorId,
                recordId,
                KafkaDeadLetterCommandType.REPLAY,
                KafkaDeadLetterCommandAuditOutcome
                    .REPLAYED,
                occurredAt
            );
    }
}

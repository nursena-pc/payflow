package com.nursena.payflow.eventprocessing.application.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class KafkaDeadLetterCommandAuditFilterTest {

    @Test
    void shouldCreateUnfilteredFilter() {
        KafkaDeadLetterCommandAuditFilter filter =
            KafkaDeadLetterCommandAuditFilter
                .unfiltered();

        assertThat(filter.commandId()).isNull();
        assertThat(filter.operatorId()).isNull();
        assertThat(filter.deadLetterRecordId())
            .isNull();
        assertThat(filter.commandType()).isNull();
        assertThat(filter.stage()).isNull();
        assertThat(filter.outcome()).isNull();
    }

    @Test
    void shouldRetainSelectedFilters() {
        UUID commandId =
            UUID.fromString(
                "b2fdb860-df65-4c43-ab69-87f930dd16dc"
            );
        UUID operatorId =
            UUID.fromString(
                "152468c4-eeba-4a17-b19c-dd0fd4ca63a7"
            );
        UUID recordId =
            UUID.fromString(
                "9f9085f8-a4bf-412d-bc3b-9c0de54ca383"
            );

        KafkaDeadLetterCommandAuditFilter filter =
            new KafkaDeadLetterCommandAuditFilter(
                commandId,
                operatorId,
                recordId,
                KafkaDeadLetterCommandType.REPLAY,
                KafkaDeadLetterCommandAuditStage
                    .COMPLETED,
                KafkaDeadLetterCommandAuditOutcome
                    .REPLAYED
            );

        assertThat(filter.commandId())
            .isEqualTo(commandId);
        assertThat(filter.operatorId())
            .isEqualTo(operatorId);
        assertThat(filter.deadLetterRecordId())
            .isEqualTo(recordId);
        assertThat(filter.commandType())
            .isEqualTo(
                KafkaDeadLetterCommandType.REPLAY
            );
        assertThat(filter.stage())
            .isEqualTo(
                KafkaDeadLetterCommandAuditStage
                    .COMPLETED
            );
        assertThat(filter.outcome())
            .isEqualTo(
                KafkaDeadLetterCommandAuditOutcome
                    .REPLAYED
            );
    }
}

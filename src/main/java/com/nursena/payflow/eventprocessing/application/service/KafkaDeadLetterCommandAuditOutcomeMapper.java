package com.nursena.payflow.eventprocessing.application.service;

import java.util.Objects;

import com.nursena.payflow.eventprocessing.application.model.DiscardKafkaDeadLetterRecordResult;
import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterCommandAuditOutcome;
import com.nursena.payflow.eventprocessing.application.model.ReplayKafkaDeadLetterRecordResult;

final class KafkaDeadLetterCommandAuditOutcomeMapper {

    private KafkaDeadLetterCommandAuditOutcomeMapper() {
    }

    static KafkaDeadLetterCommandAuditOutcome
    fromReplay(
        ReplayKafkaDeadLetterRecordResult result
    ) {
        ReplayKafkaDeadLetterRecordResult
            validatedResult =
            Objects.requireNonNull(
                result,
                "result must not be null"
            );

        return switch (validatedResult.outcome()) {
            case REPLAYED ->
                KafkaDeadLetterCommandAuditOutcome
                    .REPLAYED;

            case NOT_FOUND ->
                KafkaDeadLetterCommandAuditOutcome
                    .REPLAY_NOT_FOUND;

            case NOT_CLAIMABLE ->
                KafkaDeadLetterCommandAuditOutcome
                    .REPLAY_NOT_CLAIMABLE;

            case REPLAY_FAILED ->
                KafkaDeadLetterCommandAuditOutcome
                    .REPLAY_FAILED;

            case UNRESOLVED ->
                KafkaDeadLetterCommandAuditOutcome
                    .REPLAY_UNRESOLVED;
        };
    }

    static KafkaDeadLetterCommandAuditOutcome
    fromDiscard(
        DiscardKafkaDeadLetterRecordResult result
    ) {
        DiscardKafkaDeadLetterRecordResult
            validatedResult =
            Objects.requireNonNull(
                result,
                "result must not be null"
            );

        return switch (validatedResult.outcome()) {
            case DISCARDED ->
                KafkaDeadLetterCommandAuditOutcome
                    .DISCARDED;

            case ALREADY_DISCARDED ->
                KafkaDeadLetterCommandAuditOutcome
                    .ALREADY_DISCARDED;

            case NOT_FOUND ->
                KafkaDeadLetterCommandAuditOutcome
                    .DISCARD_NOT_FOUND;

            case NOT_DISCARDABLE ->
                KafkaDeadLetterCommandAuditOutcome
                    .DISCARD_NOT_DISCARDABLE;
        };
    }
}

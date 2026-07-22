package com.nursena.payflow.eventprocessing.adapter.out.persistence;

import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.application.model.DiscardKafkaDeadLetterRecordResult;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterDiscardPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class JdbcKafkaDeadLetterDiscardAdapter
    implements KafkaDeadLetterDiscardPort {

    private static final String DISCARD_SQL = """
        WITH target AS MATERIALIZED (
            SELECT
                id,
                status
            FROM kafka_dead_letter_records
            WHERE id = ?
            FOR UPDATE
        ),
        discarded AS (
            UPDATE kafka_dead_letter_records
                AS record
            SET status = 'DISCARDED'
            FROM target
            WHERE record.id = target.id
              AND target.status IN (
                    'RECEIVED',
                    'REPLAY_FAILED'
              )
            RETURNING record.id
        )
        SELECT
            CASE
                WHEN EXISTS (
                    SELECT 1
                    FROM discarded
                )
                    THEN 'DISCARDED'
                WHEN NOT EXISTS (
                    SELECT 1
                    FROM target
                )
                    THEN 'NOT_FOUND'
                WHEN (
                    SELECT status
                    FROM target
                ) = 'DISCARDED'
                    THEN 'ALREADY_DISCARDED'
                ELSE 'NOT_DISCARDABLE'
            END AS discard_outcome
        """;

    private final JdbcTemplate jdbcTemplate;

    JdbcKafkaDeadLetterDiscardAdapter(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            Objects.requireNonNull(
                jdbcTemplate,
                "jdbcTemplate must not be null"
            );
    }

    @Override
    public DiscardKafkaDeadLetterRecordResult
    discard(
        UUID recordId
    ) {
        UUID validatedRecordId =
            Objects.requireNonNull(
                recordId,
                "recordId must not be null"
            );

        String outcome =
            jdbcTemplate.queryForObject(
                DISCARD_SQL,
                String.class,
                validatedRecordId
            );

        return resultOf(
            Objects.requireNonNull(
                outcome,
                "Kafka dead-letter discard "
                    + "outcome must not be null"
            )
        );
    }

    private static DiscardKafkaDeadLetterRecordResult
    resultOf(
        String outcome
    ) {
        return switch (outcome) {
            case "DISCARDED" ->
                DiscardKafkaDeadLetterRecordResult
                    .discarded();

            case "ALREADY_DISCARDED" ->
                DiscardKafkaDeadLetterRecordResult
                    .alreadyDiscarded();

            case "NOT_FOUND" ->
                DiscardKafkaDeadLetterRecordResult
                    .notFound();

            case "NOT_DISCARDABLE" ->
                DiscardKafkaDeadLetterRecordResult
                    .notDiscardable();

            default ->
                throw new IllegalStateException(
                    "Unknown Kafka dead-letter "
                        + "discard outcome: "
                        + outcome
                );
        };
    }
}

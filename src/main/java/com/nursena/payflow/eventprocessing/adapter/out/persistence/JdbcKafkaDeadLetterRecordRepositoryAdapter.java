package com.nursena.payflow.eventprocessing.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;

import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterRecordRepositoryPort;
import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcKafkaDeadLetterRecordRepositoryAdapter
    implements KafkaDeadLetterRecordRepositoryPort {

    private static final String INSERT_SQL = """
        INSERT INTO kafka_dead_letter_records (
            id,
            dlt_topic,
            dlt_partition,
            dlt_offset,
            original_topic,
            original_partition,
            original_offset,
            original_consumer_group,
            record_key,
            payload,
            exception_type,
            exception_message,
            status,
            replay_count,
            received_at,
            last_replayed_at,
            replay_lease_owner,
            replay_lease_until,
            last_replay_error
        )
        VALUES (
            ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
            ?, ?, ?, ?, ?, ?, ?, ?, ?
        )
        ON CONFLICT (
            dlt_topic,
            dlt_partition,
            dlt_offset
        )
        DO NOTHING
        """;

    private final JdbcTemplate jdbcTemplate;

    JdbcKafkaDeadLetterRecordRepositoryAdapter(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            Objects.requireNonNull(
                jdbcTemplate,
                "jdbcTemplate must not be null"
            );
    }

    @Override
    public boolean tryRecord(
        KafkaDeadLetterRecord record
    ) {
        Objects.requireNonNull(
            record,
            "record must not be null"
        );

        int affectedRows =
            jdbcTemplate.update(
                INSERT_SQL,
                record.id(),
                record.deadLetterTopic(),
                record.deadLetterPartition(),
                record.deadLetterOffset(),
                record.originalTopic(),
                record.originalPartition(),
                record.originalOffset(),
                record.originalConsumerGroup(),
                record.recordKey(),
                record.payload(),
                record.exceptionType(),
                record.exceptionMessage(),
                record.status().name(),
                record.replayCount(),
                timestamp(
                    record.receivedAt()
                ),
                timestamp(
                    record.lastReplayedAt()
                ),
                record.replayLeaseOwner(),
                timestamp(
                    record.replayLeaseUntil()
                ),
                record.lastReplayError()
            );

        if (affectedRows == 1) {
            return true;
        }

        if (affectedRows == 0) {
            return false;
        }

        throw new IllegalStateException(
            "Kafka dead-letter record insert "
                + "affected "
                + affectedRows
                + " rows."
        );
    }

    private static Timestamp timestamp(
        Instant value
    ) {
        if (value == null) {
            return null;
        }

        return Timestamp.from(value);
    }
}

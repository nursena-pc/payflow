package com.nursena.payflow.eventprocessing.adapter.out.persistence;

import java.sql.Timestamp;
import java.util.Objects;

import com.nursena.payflow.eventprocessing.application.port.out.ProcessedKafkaEventRepositoryPort;
import com.nursena.payflow.eventprocessing.domain.model.ProcessedKafkaEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcProcessedKafkaEventRepositoryAdapter
    implements ProcessedKafkaEventRepositoryPort {

    private static final String INSERT_SQL = """
        INSERT INTO processed_kafka_events (
            consumer_name,
            event_id,
            event_type,
            event_version,
            topic,
            partition_number,
            record_offset,
            processed_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (
            consumer_name,
            event_id
        )
        DO NOTHING
        """;

    private final JdbcTemplate jdbcTemplate;

    JdbcProcessedKafkaEventRepositoryAdapter(
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
        ProcessedKafkaEvent event
    ) {
        Objects.requireNonNull(
            event,
            "event must not be null"
        );

        int affectedRows = jdbcTemplate.update(
            INSERT_SQL,
            event.consumerName(),
            event.eventId(),
            event.eventType(),
            event.eventVersion(),
            event.topic(),
            event.partitionNumber(),
            event.recordOffset(),
            Timestamp.from(
                event.processedAt()
            )
        );

        if (affectedRows == 1) {
            return true;
        }

        if (affectedRows == 0) {
            return false;
        }

        throw new IllegalStateException(
            "Processed Kafka event insert affected "
                + affectedRows
                + " rows."
        );
    }
}

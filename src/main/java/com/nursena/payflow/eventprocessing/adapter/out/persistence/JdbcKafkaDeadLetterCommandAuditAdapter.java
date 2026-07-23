package com.nursena.payflow.eventprocessing.adapter.out.persistence;

import java.sql.Timestamp;
import java.util.Objects;

import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterCommandAudit;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterCommandAuditPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcKafkaDeadLetterCommandAuditAdapter
    implements KafkaDeadLetterCommandAuditPort {

    private static final String INSERT_SQL = """
        INSERT INTO kafka_dead_letter_command_audits (
            id,
            command_id,
            stage,
            operator_id,
            dead_letter_record_id,
            command_type,
            outcome,
            error_code,
            occurred_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    private final JdbcTemplate jdbcTemplate;

    JdbcKafkaDeadLetterCommandAuditAdapter(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            Objects.requireNonNull(
                jdbcTemplate,
                "jdbcTemplate must not be null"
            );
    }

    @Override
    @Transactional(
        propagation = Propagation.REQUIRES_NEW
    )
    public void append(
        KafkaDeadLetterCommandAudit audit
    ) {
        KafkaDeadLetterCommandAudit
            validatedAudit =
            Objects.requireNonNull(
                audit,
                "audit must not be null"
            );

        int affectedRows = jdbcTemplate.update(
            INSERT_SQL,
            validatedAudit.id(),
            validatedAudit.commandId(),
            validatedAudit.stage().name(),
            validatedAudit.operatorId(),
            validatedAudit.deadLetterRecordId(),
            validatedAudit.commandType().name(),
            enumName(
                validatedAudit.outcome()
            ),
            validatedAudit.errorCode(),
            Timestamp.from(
                validatedAudit.occurredAt()
            )
        );

        if (affectedRows != 1) {
            throw new IllegalStateException(
                "Kafka dead-letter command audit "
                    + "insert affected "
                    + affectedRows
                    + " rows."
            );
        }
    }

    private static String enumName(
        Enum<?> value
    ) {
        if (value == null) {
            return null;
        }

        return value.name();
    }
}

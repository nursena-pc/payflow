package com.nursena.payflow.eventprocessing.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterCommandAudit;
import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterCommandAuditOutcome;
import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterCommandType;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterCommandAuditPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class KafkaDeadLetterCommandAuditPersistenceIntegrationTest {

    private static final UUID COMMAND_ID =
        UUID.fromString(
            "90000000-0000-0000-0000-000000000101"
        );

    private static final UUID OPERATOR_ID =
        UUID.fromString(
            "91000000-0000-0000-0000-000000000101"
        );

    private static final UUID RECORD_ID =
        UUID.fromString(
            "92000000-0000-0000-0000-000000000101"
        );

    private static final Instant OCCURRED_AT =
        Instant.parse(
            "2026-07-23T13:00:00.123456789Z"
        );

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
            "postgres:17-alpine"
        );

    @Autowired
    private KafkaDeadLetterCommandAuditPort
        auditPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE "
                + "kafka_dead_letter_command_audits"
        );
    }

    @Test
    void shouldPersistAttemptedAudit() {
        UUID auditId =
            UUID.fromString(
                "93000000-0000-0000-0000-000000000101"
            );

        auditPort.append(
            KafkaDeadLetterCommandAudit.attempted(
                auditId,
                COMMAND_ID,
                OPERATOR_ID,
                RECORD_ID,
                KafkaDeadLetterCommandType.REPLAY,
                OCCURRED_AT
            )
        );

        Map<String, Object> stored =
            jdbcTemplate.queryForMap(
                """
                SELECT
                    command_id,
                    stage,
                    operator_id,
                    dead_letter_record_id,
                    command_type,
                    outcome,
                    error_code,
                    occurred_at
                FROM kafka_dead_letter_command_audits
                WHERE id = ?
                """,
                auditId
            );

        assertThat(stored.get("command_id"))
            .isEqualTo(COMMAND_ID);

        assertThat(stored.get("stage"))
            .isEqualTo("ATTEMPTED");

        assertThat(stored.get("operator_id"))
            .isEqualTo(OPERATOR_ID);

        assertThat(
            stored.get("dead_letter_record_id")
        )
            .isEqualTo(RECORD_ID);

        assertThat(stored.get("command_type"))
            .isEqualTo("REPLAY");

        assertThat(stored.get("outcome"))
            .isNull();

        assertThat(stored.get("error_code"))
            .isNull();

        assertThat(stored.get("occurred_at"))
            .isEqualTo(
                Timestamp.from(
                    Instant.parse(
                        "2026-07-23T13:00:00.123456Z"
                    )
                )
            );
    }

    @Test
    void shouldPersistCompletedAuditWithSafeErrorCode() {
        auditPort.append(
            KafkaDeadLetterCommandAudit.attempted(
                UUID.fromString(
                    "93000000-0000-0000-0000-000000000102"
                ),
                COMMAND_ID,
                OPERATOR_ID,
                RECORD_ID,
                KafkaDeadLetterCommandType.REPLAY,
                OCCURRED_AT
            )
        );

        UUID completedAuditId =
            UUID.fromString(
                "93000000-0000-0000-0000-000000000103"
            );

        auditPort.append(
            KafkaDeadLetterCommandAudit.completed(
                completedAuditId,
                COMMAND_ID,
                OPERATOR_ID,
                RECORD_ID,
                KafkaDeadLetterCommandType.REPLAY,
                KafkaDeadLetterCommandAuditOutcome
                    .REPLAY_NOT_CLAIMABLE,
                OCCURRED_AT.plusSeconds(1)
            )
        );

        Map<String, Object> stored =
            jdbcTemplate.queryForMap(
                """
                SELECT
                    stage,
                    command_type,
                    outcome,
                    error_code
                FROM kafka_dead_letter_command_audits
                WHERE id = ?
                """,
                completedAuditId
            );

        assertThat(stored.get("stage"))
            .isEqualTo("COMPLETED");

        assertThat(stored.get("command_type"))
            .isEqualTo("REPLAY");

        assertThat(stored.get("outcome"))
            .isEqualTo(
                "REPLAY_NOT_CLAIMABLE"
            );

        assertThat(stored.get("error_code"))
            .isEqualTo(
                "KAFKA_DEAD_LETTER_RECORD_NOT_CLAIMABLE"
            );
    }
}

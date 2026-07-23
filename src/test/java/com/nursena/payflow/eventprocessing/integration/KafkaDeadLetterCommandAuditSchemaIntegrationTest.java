package com.nursena.payflow.eventprocessing.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class KafkaDeadLetterCommandAuditSchemaIntegrationTest {

    private static final UUID AUDIT_ID =
        UUID.fromString(
            "93000000-0000-0000-0000-000000000201"
        );

    private static final UUID COMMAND_ID =
        UUID.fromString(
            "90000000-0000-0000-0000-000000000201"
        );

    private static final UUID OPERATOR_ID =
        UUID.fromString(
            "91000000-0000-0000-0000-000000000201"
        );

    private static final UUID RECORD_ID =
        UUID.fromString(
            "92000000-0000-0000-0000-000000000201"
        );

    private static final Instant OCCURRED_AT =
        Instant.parse(
            "2026-07-23T13:00:00Z"
        );

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
            "postgres:17-alpine"
        );

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
    void shouldPersistValidAttemptedAudit() {
        insertAudit(
            AUDIT_ID,
            COMMAND_ID,
            "ATTEMPTED",
            "REPLAY",
            null,
            null
        );

        assertThat(countByAuditId(AUDIT_ID))
            .isEqualTo(1);
    }

    @Test
    void shouldPersistValidCompletedAudit() {
        insertAudit(
            AUDIT_ID,
            COMMAND_ID,
            "COMPLETED",
            "DISCARD",
            "DISCARDED",
            null
        );

        assertThat(countByAuditId(AUDIT_ID))
            .isEqualTo(1);
    }

    @Test
    void shouldRejectDuplicateCommandStage() {
        insertAudit(
            AUDIT_ID,
            COMMAND_ID,
            "ATTEMPTED",
            "REPLAY",
            null,
            null
        );

        assertConstraintViolation(
            () -> insertAudit(
                UUID.fromString(
                    "93000000-0000-0000-0000-000000000202"
                ),
                COMMAND_ID,
                "ATTEMPTED",
                "REPLAY",
                null,
                null
            ),
            "uq_kafka_dead_letter_command_audits_command_stage"
        );
    }

    @Test
    void shouldRejectUnsupportedStage() {
        assertDataIntegrityViolation(
            () -> insertAudit(
                AUDIT_ID,
                COMMAND_ID,
                "STARTED",
                "REPLAY",
                null,
                null
            )
        );
    }

    @Test
    void shouldRejectUnsupportedCommandType() {
        assertConstraintViolation(
            () -> insertAudit(
                AUDIT_ID,
                COMMAND_ID,
                "ATTEMPTED",
                "RETRY",
                null,
                null
            ),
            "chk_kafka_dead_letter_command_audits_type"
        );
    }

    @Test
    void shouldRejectUnsupportedOutcome() {
        assertDataIntegrityViolation(
            () -> insertAudit(
                AUDIT_ID,
                COMMAND_ID,
                "COMPLETED",
                "REPLAY",
                "REPLAY_REJECTED",
                null
            )
        );
    }

    @Test
    void shouldRejectUnsupportedErrorCode() {
        assertDataIntegrityViolation(
            () -> insertAudit(
                AUDIT_ID,
                COMMAND_ID,
                "COMPLETED",
                "REPLAY",
                "REPLAY_FAILED",
                "RAW_EXCEPTION_MESSAGE"
            )
        );
    }

    @Test
    void shouldRejectOutcomeOnAttemptedAudit() {
        assertConstraintViolation(
            () -> insertAudit(
                AUDIT_ID,
                COMMAND_ID,
                "ATTEMPTED",
                "REPLAY",
                "REPLAYED",
                null
            ),
            "chk_kafka_dead_letter_command_audits_stage_state"
        );
    }

    @Test
    void shouldRejectMissingOutcomeOnCompletedAudit() {
        assertConstraintViolation(
            () -> insertAudit(
                AUDIT_ID,
                COMMAND_ID,
                "COMPLETED",
                "REPLAY",
                null,
                null
            ),
            "chk_kafka_dead_letter_command_audits_stage_state"
        );
    }

    @Test
    void shouldRejectOutcomeForDifferentCommandType() {
        assertConstraintViolation(
            () -> insertAudit(
                AUDIT_ID,
                COMMAND_ID,
                "COMPLETED",
                "DISCARD",
                "REPLAYED",
                null
            ),
            "chk_kafka_dead_letter_command_audits_command_outcome"
        );
    }

    @Test
    void shouldRejectErrorCodeInconsistentWithOutcome() {
        assertConstraintViolation(
            () -> insertAudit(
                AUDIT_ID,
                COMMAND_ID,
                "COMPLETED",
                "REPLAY",
                "REPLAY_FAILED",
                "KAFKA_DEAD_LETTER_REPLAY_UNRESOLVED"
            ),
            "chk_kafka_dead_letter_command_audits_outcome_error"
        );
    }

    @Test
    void shouldRejectUpdate() {
        insertAudit(
            AUDIT_ID,
            COMMAND_ID,
            "ATTEMPTED",
            "REPLAY",
            null,
            null
        );

        assertAppendOnlyViolation(
            () -> jdbcTemplate.update(
                """
                UPDATE kafka_dead_letter_command_audits
                SET operator_id = ?
                WHERE id = ?
                """,
                UUID.fromString(
                    "91000000-0000-0000-0000-000000000202"
                ),
                AUDIT_ID
            )
        );
    }

    @Test
    void shouldRejectDelete() {
        insertAudit(
            AUDIT_ID,
            COMMAND_ID,
            "ATTEMPTED",
            "REPLAY",
            null,
            null
        );

        assertAppendOnlyViolation(
            () -> jdbcTemplate.update(
                """
                DELETE FROM kafka_dead_letter_command_audits
                WHERE id = ?
                """,
                AUDIT_ID
            )
        );
    }

    private Integer countByAuditId(
        UUID auditId
    ) {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM kafka_dead_letter_command_audits
            WHERE id = ?
            """,
            Integer.class,
            auditId
        );
    }

    private void insertAudit(
        UUID auditId,
        UUID commandId,
        String stage,
        String commandType,
        String outcome,
        String errorCode
    ) {
        jdbcTemplate.update(
            """
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
            """,
            auditId,
            commandId,
            stage,
            OPERATOR_ID,
            RECORD_ID,
            commandType,
            outcome,
            errorCode,
            Timestamp.from(OCCURRED_AT)
        );
    }

    private static void assertConstraintViolation(
        ThrowingCallable operation,
        String constraintName
    ) {
        Throwable thrown = catchThrowable(
            operation
        );

        assertThat(thrown)
            .isInstanceOf(
                DataIntegrityViolationException.class
            );

        assertThat(rootCauseOf(thrown).getMessage())
            .contains(constraintName);
    }

    private static void assertDataIntegrityViolation(
        ThrowingCallable operation
    ) {
        assertThat(catchThrowable(operation))
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    private static void assertAppendOnlyViolation(
        ThrowingCallable operation
    ) {
        Throwable thrown = catchThrowable(
            operation
        );

        assertThat(thrown)
            .isInstanceOf(
                DataAccessException.class
            );

        assertThat(rootCauseOf(thrown).getMessage())
            .contains(
                "kafka_dead_letter_command_audits "
                    + "is append-only"
            );
    }

    private static Throwable rootCauseOf(
        Throwable throwable
    ) {
        Throwable current = throwable;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        return current;
    }
}

package com.nursena.payflow.eventprocessing.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterCommandAudit;
import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterCommandType;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterCommandAuditPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class KafkaDeadLetterCommandAuditTransactionIntegrationTest {

    private static final UUID AUDIT_ID =
        UUID.fromString(
            "93000000-0000-0000-0000-000000000301"
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
    private PlatformTransactionManager
        transactionManager;

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
    void shouldCommitAuditWhenOuterTransactionRollsBack() {
        TransactionTemplate outerTransaction =
            new TransactionTemplate(
                transactionManager
            );

        assertThatThrownBy(
            () -> outerTransaction.executeWithoutResult(
                status -> {
                    auditPort.append(
                        attemptedAudit()
                    );

                    throw new OuterTransactionFailure();
                }
            )
        )
            .isInstanceOf(
                OuterTransactionFailure.class
            );

        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM kafka_dead_letter_command_audits
            WHERE id = ?
            """,
            Integer.class,
            AUDIT_ID
        );

        assertThat(count)
            .isEqualTo(1);
    }

    private static KafkaDeadLetterCommandAudit
    attemptedAudit() {
        return KafkaDeadLetterCommandAudit.attempted(
            AUDIT_ID,
            UUID.fromString(
                "90000000-0000-0000-0000-000000000301"
            ),
            UUID.fromString(
                "91000000-0000-0000-0000-000000000301"
            ),
            UUID.fromString(
                "92000000-0000-0000-0000-000000000301"
            ),
            KafkaDeadLetterCommandType.REPLAY,
            Instant.parse(
                "2026-07-23T13:00:00Z"
            )
        );
    }

    private static final class
    OuterTransactionFailure extends RuntimeException {

        private static final long serialVersionUID = 1L;
    }
}

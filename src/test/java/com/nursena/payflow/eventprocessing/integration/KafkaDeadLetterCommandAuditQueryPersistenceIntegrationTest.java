package com.nursena.payflow.eventprocessing.integration;

import static org.assertj.core.api.Assertions
    .assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAudit;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAuditFilter;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAuditOutcome;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAuditPage;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAuditStage;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandAuditTimeline;
import com.nursena.payflow.eventprocessing.application.model
    .KafkaDeadLetterCommandType;
import com.nursena.payflow.eventprocessing.application.port.out
    .KafkaDeadLetterCommandAuditQueryPort;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection
    .ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class KafkaDeadLetterCommandAuditQueryPersistenceIntegrationTest {

    private static final UUID OPERATOR_ONE =
        uuid("10000000-0000-0000-0000-000000001001");

    private static final UUID OPERATOR_TWO =
        uuid("10000000-0000-0000-0000-000000001002");

    private static final UUID RECORD_ONE =
        uuid("20000000-0000-0000-0000-000000001001");

    private static final UUID RECORD_TWO =
        uuid("20000000-0000-0000-0000-000000001002");

    private static final UUID RECORD_THREE =
        uuid("20000000-0000-0000-0000-000000001003");

    private static final UUID RECORD_FOUR =
        uuid("20000000-0000-0000-0000-000000001004");

    private static final UUID COMMAND_ONE =
        uuid("30000000-0000-0000-0000-000000001001");

    private static final UUID COMMAND_TWO =
        uuid("30000000-0000-0000-0000-000000001002");

    private static final UUID COMMAND_THREE =
        uuid("30000000-0000-0000-0000-000000001003");

    private static final UUID COMMAND_FOUR =
        uuid("30000000-0000-0000-0000-000000001004");

    private static final UUID AUDIT_ONE_ATTEMPTED =
        uuid("40000000-0000-0000-0000-000000001011");

    private static final UUID AUDIT_ONE_COMPLETED =
        uuid("40000000-0000-0000-0000-000000001012");

    private static final UUID AUDIT_TWO_ATTEMPTED =
        uuid("40000000-0000-0000-0000-000000001021");

    private static final UUID AUDIT_THREE_ATTEMPTED =
        uuid("40000000-0000-0000-0000-000000001031");

    private static final UUID AUDIT_THREE_COMPLETED =
        uuid("40000000-0000-0000-0000-000000001032");

    private static final UUID AUDIT_FOUR_ATTEMPTED =
        uuid("40000000-0000-0000-0000-000000001041");

    private static final UUID AUDIT_FOUR_COMPLETED =
        uuid("40000000-0000-0000-0000-000000001042");

    private static final Instant BASE_TIME =
        Instant.parse("2026-07-25T12:00:00Z");

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
            "postgres:17-alpine"
        );

    @Autowired
    private KafkaDeadLetterCommandAuditQueryPort queryPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void prepareDatabase() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE "
                + "kafka_dead_letter_command_audits"
        );

        insertFixtures();
    }

    @Test
    void shouldListAuditsUsingDeterministicPagination() {
        KafkaDeadLetterCommandAuditPage firstPage =
            queryPort.findPage(
                0,
                3,
                KafkaDeadLetterCommandAuditFilter
                    .unfiltered()
            );

        assertThat(firstPage.items())
            .extracting(
                KafkaDeadLetterCommandAudit::id
            )
            .containsExactly(
                AUDIT_FOUR_COMPLETED,
                AUDIT_FOUR_ATTEMPTED,
                AUDIT_THREE_COMPLETED
            );

        assertThat(firstPage.totalElements())
            .isEqualTo(7L);

        assertThat(firstPage.totalPages())
            .isEqualTo(3);

        assertThat(firstPage.first())
            .isTrue();

        assertThat(firstPage.hasNext())
            .isTrue();

        KafkaDeadLetterCommandAuditPage secondPage =
            queryPort.findPage(
                1,
                3,
                KafkaDeadLetterCommandAuditFilter
                    .unfiltered()
            );

        assertThat(secondPage.items())
            .extracting(
                KafkaDeadLetterCommandAudit::id
            )
            .containsExactly(
                AUDIT_THREE_ATTEMPTED,
                AUDIT_TWO_ATTEMPTED,
                AUDIT_ONE_COMPLETED
            );
    }

    @Test
    void shouldApplyAllSupportedFiltersTogether() {
        KafkaDeadLetterCommandAuditFilter filter =
            new KafkaDeadLetterCommandAuditFilter(
                COMMAND_THREE,
                OPERATOR_ONE,
                RECORD_THREE,
                KafkaDeadLetterCommandType.REPLAY,
                KafkaDeadLetterCommandAuditStage
                    .COMPLETED,
                KafkaDeadLetterCommandAuditOutcome
                    .REPLAY_FAILED
            );

        KafkaDeadLetterCommandAuditPage page =
            queryPort.findPage(
                0,
                20,
                filter
            );

        assertThat(page.items())
            .extracting(
                KafkaDeadLetterCommandAudit::id
            )
            .containsExactly(
                AUDIT_THREE_COMPLETED
            );

        assertThat(page.totalElements())
            .isOne();
    }

    @Test
    void shouldFilterByStageAndOutcomeIndependently() {
        KafkaDeadLetterCommandAuditPage completed =
            queryPort.findPage(
                0,
                20,
                new KafkaDeadLetterCommandAuditFilter(
                    null,
                    null,
                    null,
                    null,
                    KafkaDeadLetterCommandAuditStage
                        .COMPLETED,
                    null
                )
            );

        assertThat(completed.items())
            .extracting(
                KafkaDeadLetterCommandAudit::id
            )
            .containsExactly(
                AUDIT_FOUR_COMPLETED,
                AUDIT_THREE_COMPLETED,
                AUDIT_ONE_COMPLETED
            );

        KafkaDeadLetterCommandAuditPage failed =
            queryPort.findPage(
                0,
                20,
                new KafkaDeadLetterCommandAuditFilter(
                    null,
                    null,
                    null,
                    null,
                    null,
                    KafkaDeadLetterCommandAuditOutcome
                        .REPLAY_FAILED
                )
            );

        assertThat(failed.items())
            .extracting(
                KafkaDeadLetterCommandAudit::id
            )
            .containsExactly(
                AUDIT_THREE_COMPLETED
            );
    }

    @Test
    void shouldReturnEmptyPageForUnmatchedFilter() {
        KafkaDeadLetterCommandAuditPage page =
            queryPort.findPage(
                0,
                20,
                new KafkaDeadLetterCommandAuditFilter(
                    null,
                    null,
                    null,
                    null,
                    null,
                    KafkaDeadLetterCommandAuditOutcome
                        .REPLAY_NOT_FOUND
                )
            );

        assertThat(page.items())
            .isEmpty();

        assertThat(page.totalElements())
            .isZero();

        assertThat(page.totalPages())
            .isZero();

        assertThat(page.first())
            .isTrue();

        assertThat(page.last())
            .isTrue();
    }

    @Test
    void shouldReturnCompleteTimelineChronologically() {
        KafkaDeadLetterCommandAuditTimeline timeline =
            queryPort.findTimelineByCommandId(
                    COMMAND_FOUR
                )
                .orElseThrow();

        assertThat(timeline.commandId())
            .isEqualTo(COMMAND_FOUR);

        assertThat(timeline.complete())
            .isTrue();

        assertThat(timeline.entries())
            .extracting(
                KafkaDeadLetterCommandAudit::id
            )
            .containsExactly(
                AUDIT_FOUR_ATTEMPTED,
                AUDIT_FOUR_COMPLETED
            );
    }

    @Test
    void shouldReturnIncompleteAttemptedOnlyTimeline() {
        KafkaDeadLetterCommandAuditTimeline timeline =
            queryPort.findTimelineByCommandId(
                    COMMAND_TWO
                )
                .orElseThrow();

        assertThat(timeline.complete())
            .isFalse();

        assertThat(timeline.entries())
            .extracting(
                KafkaDeadLetterCommandAudit::id
            )
            .containsExactly(
                AUDIT_TWO_ATTEMPTED
            );
    }

    @Test
    void shouldReturnEmptyForUnknownCommandIdentifier() {
        assertThat(
            queryPort.findTimelineByCommandId(
                uuid(
                    "30000000-0000-0000-0000-000000001099"
                )
            )
        )
            .isEmpty();
    }

    @Test
    void shouldCreateDeterministicQueryIndexes() {
        Map<String, String> definitions =
            indexDefinitions();

        assertThat(definitions)
            .containsKeys(
                "idx_kafka_dead_letter_command_"
                    + "audits_occurred_id",
                "idx_kafka_dead_letter_command_"
                    + "audits_operator_time_id",
                "idx_kafka_dead_letter_command_"
                    + "audits_record_time_id",
                "idx_kafka_dead_letter_command_"
                    + "audits_type_time_id",
                "idx_kafka_dead_letter_command_"
                    + "audits_stage_time_id",
                "idx_kafka_dead_letter_command_"
                    + "audits_outcome_time_id"
            );

        assertThat(
            definitions.get(
                "idx_kafka_dead_letter_command_"
                    + "audits_occurred_id"
            )
        )
            .contains(
                "(occurred_at DESC, id DESC)"
            );

        assertThat(
            definitions.get(
                "idx_kafka_dead_letter_command_"
                    + "audits_operator_time_id"
            )
        )
            .contains(
                "(operator_id, occurred_at DESC, "
                    + "id DESC)"
            );

        assertThat(definitions)
            .doesNotContainKeys(
                "idx_kafka_dead_letter_command_"
                    + "audits_operator_time",
                "idx_kafka_dead_letter_command_"
                    + "audits_record_time",
                "idx_kafka_dead_letter_command_"
                    + "audits_type_time",
                "idx_kafka_dead_letter_command_"
                    + "audits_occurred_at"
            );
    }

    private void insertFixtures() {
        insertAttempted(
            AUDIT_ONE_ATTEMPTED,
            COMMAND_ONE,
            OPERATOR_ONE,
            RECORD_ONE,
            KafkaDeadLetterCommandType.REPLAY,
            BASE_TIME.plusSeconds(10)
        );

        insertCompleted(
            AUDIT_ONE_COMPLETED,
            COMMAND_ONE,
            OPERATOR_ONE,
            RECORD_ONE,
            KafkaDeadLetterCommandType.REPLAY,
            KafkaDeadLetterCommandAuditOutcome
                .REPLAYED,
            BASE_TIME.plusSeconds(20)
        );

        insertAttempted(
            AUDIT_TWO_ATTEMPTED,
            COMMAND_TWO,
            OPERATOR_TWO,
            RECORD_TWO,
            KafkaDeadLetterCommandType.DISCARD,
            BASE_TIME.plusSeconds(30)
        );

        insertAttempted(
            AUDIT_THREE_ATTEMPTED,
            COMMAND_THREE,
            OPERATOR_ONE,
            RECORD_THREE,
            KafkaDeadLetterCommandType.REPLAY,
            BASE_TIME.plusSeconds(40)
        );

        insertCompleted(
            AUDIT_THREE_COMPLETED,
            COMMAND_THREE,
            OPERATOR_ONE,
            RECORD_THREE,
            KafkaDeadLetterCommandType.REPLAY,
            KafkaDeadLetterCommandAuditOutcome
                .REPLAY_FAILED,
            BASE_TIME.plusSeconds(50)
        );

        insertAttempted(
            AUDIT_FOUR_ATTEMPTED,
            COMMAND_FOUR,
            OPERATOR_TWO,
            RECORD_FOUR,
            KafkaDeadLetterCommandType.DISCARD,
            BASE_TIME.plusSeconds(60)
        );

        insertCompleted(
            AUDIT_FOUR_COMPLETED,
            COMMAND_FOUR,
            OPERATOR_TWO,
            RECORD_FOUR,
            KafkaDeadLetterCommandType.DISCARD,
            KafkaDeadLetterCommandAuditOutcome
                .DISCARDED,
            BASE_TIME.plusSeconds(60)
        );
    }

    private void insertAttempted(
        UUID id,
        UUID commandId,
        UUID operatorId,
        UUID recordId,
        KafkaDeadLetterCommandType commandType,
        Instant occurredAt
    ) {
        insertAudit(
            id,
            commandId,
            KafkaDeadLetterCommandAuditStage
                .ATTEMPTED,
            operatorId,
            recordId,
            commandType,
            null,
            null,
            occurredAt
        );
    }

    private void insertCompleted(
        UUID id,
        UUID commandId,
        UUID operatorId,
        UUID recordId,
        KafkaDeadLetterCommandType commandType,
        KafkaDeadLetterCommandAuditOutcome outcome,
        Instant occurredAt
    ) {
        insertAudit(
            id,
            commandId,
            KafkaDeadLetterCommandAuditStage
                .COMPLETED,
            operatorId,
            recordId,
            commandType,
            outcome,
            outcome.safeErrorCode(),
            occurredAt
        );
    }

    private void insertAudit(
        UUID id,
        UUID commandId,
        KafkaDeadLetterCommandAuditStage stage,
        UUID operatorId,
        UUID recordId,
        KafkaDeadLetterCommandType commandType,
        KafkaDeadLetterCommandAuditOutcome outcome,
        String errorCode,
        Instant occurredAt
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
            id,
            commandId,
            stage.name(),
            operatorId,
            recordId,
            commandType.name(),
            outcome == null
                ? null
                : outcome.name(),
            errorCode,
            Timestamp.from(occurredAt)
        );
    }

    private Map<String, String> indexDefinitions() {
        return jdbcTemplate.query(
            """
            SELECT
                indexname,
                indexdef
            FROM pg_indexes
            WHERE schemaname = current_schema()
              AND tablename =
                    'kafka_dead_letter_command_audits'
            """,
            resultSet -> {
                Map<String, String> result =
                    new LinkedHashMap<>();

                while (resultSet.next()) {
                    result.put(
                        resultSet.getString(
                            "indexname"
                        ),
                        resultSet.getString(
                            "indexdef"
                        )
                    );
                }

                return result;
            }
        );
    }

    private static UUID uuid(
        String value
    ) {
        return UUID.fromString(value);
    }
}

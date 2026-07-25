package com.nursena.payflow.eventprocessing.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcKafkaDeadLetterCommandAuditQueryAdapter
    implements KafkaDeadLetterCommandAuditQueryPort {

    private static final String AUDIT_SELECT = """
        SELECT
            id,
            command_id,
            stage,
            operator_id,
            dead_letter_record_id,
            command_type,
            outcome,
            error_code,
            occurred_at
        FROM kafka_dead_letter_command_audits
        """;

    private static final String COUNT_SELECT = """
        SELECT COUNT(*)
        FROM kafka_dead_letter_command_audits
        """;

    private static final String PAGE_ORDER = """
        ORDER BY
            occurred_at DESC,
            id DESC
        LIMIT ?
        OFFSET ?
        """;

    private static final String TIMELINE_QUERY =
        AUDIT_SELECT + """
        WHERE command_id = ?
        ORDER BY
            occurred_at ASC,
            id ASC
        """;

    private final JdbcTemplate jdbcTemplate;

    JdbcKafkaDeadLetterCommandAuditQueryAdapter(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            Objects.requireNonNull(
                jdbcTemplate,
                "jdbcTemplate must not be null"
            );
    }

    @Override
    public KafkaDeadLetterCommandAuditPage findPage(
        int page,
        int size,
        KafkaDeadLetterCommandAuditFilter filter
    ) {
        validatePage(page);
        validateSize(size);

        KafkaDeadLetterCommandAuditFilter
            validatedFilter =
            Objects.requireNonNull(
                filter,
                "filter must not be null"
            );

        QueryCriteria criteria =
            queryCriteria(validatedFilter);

        long totalElements = count(criteria);

        List<KafkaDeadLetterCommandAudit> items =
            totalElements == 0
                ? List.of()
                : findAudits(
                    page,
                    size,
                    criteria
                );

        return new KafkaDeadLetterCommandAuditPage(
            items,
            page,
            size,
            totalElements,
            calculateTotalPages(
                totalElements,
                size
            )
        );
    }

    @Override
    public Optional<KafkaDeadLetterCommandAuditTimeline>
    findTimelineByCommandId(
        UUID commandId
    ) {
        UUID validatedCommandId =
            Objects.requireNonNull(
                commandId,
                "commandId must not be null"
            );

        List<KafkaDeadLetterCommandAudit> entries =
            jdbcTemplate.query(
                TIMELINE_QUERY,
                JdbcKafkaDeadLetterCommandAuditQueryAdapter
                    ::mapAudit,
                validatedCommandId
            );

        if (entries.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(
            new KafkaDeadLetterCommandAuditTimeline(
                validatedCommandId,
                entries
            )
        );
    }

    private List<KafkaDeadLetterCommandAudit>
    findAudits(
        int page,
        int size,
        QueryCriteria criteria
    ) {
        List<Object> arguments =
            new ArrayList<>(criteria.arguments());

        arguments.add(size);
        arguments.add(
            calculateOffset(
                page,
                size
            )
        );

        return jdbcTemplate.query(
            AUDIT_SELECT
                + criteria.whereClause()
                + PAGE_ORDER,
            JdbcKafkaDeadLetterCommandAuditQueryAdapter
                ::mapAudit,
            arguments.toArray()
        );
    }

    private long count(
        QueryCriteria criteria
    ) {
        Long result =
            jdbcTemplate.queryForObject(
                COUNT_SELECT
                    + criteria.whereClause(),
                Long.class,
                criteria.arguments().toArray()
            );

        return Objects.requireNonNull(
            result,
            "Kafka dead-letter command audit count "
                + "must not be null"
        );
    }

    private static QueryCriteria queryCriteria(
        KafkaDeadLetterCommandAuditFilter filter
    ) {
        List<String> predicates =
            new ArrayList<>();

        List<Object> arguments =
            new ArrayList<>();

        addFilter(
            predicates,
            arguments,
            "command_id = ?",
            filter.commandId()
        );

        addFilter(
            predicates,
            arguments,
            "operator_id = ?",
            filter.operatorId()
        );

        addFilter(
            predicates,
            arguments,
            "dead_letter_record_id = ?",
            filter.deadLetterRecordId()
        );

        addEnumFilter(
            predicates,
            arguments,
            "command_type = ?",
            filter.commandType()
        );

        addEnumFilter(
            predicates,
            arguments,
            "stage = ?",
            filter.stage()
        );

        addEnumFilter(
            predicates,
            arguments,
            "outcome = ?",
            filter.outcome()
        );

        String whereClause = predicates.isEmpty()
            ? ""
            : "WHERE "
                + String.join(
                    " AND ",
                    predicates
                )
                + System.lineSeparator();

        return new QueryCriteria(
            whereClause,
            arguments
        );
    }

    private static void addFilter(
        List<String> predicates,
        List<Object> arguments,
        String predicate,
        Object value
    ) {
        if (value == null) {
            return;
        }

        predicates.add(predicate);
        arguments.add(value);
    }

    private static void addEnumFilter(
        List<String> predicates,
        List<Object> arguments,
        String predicate,
        Enum<?> value
    ) {
        addFilter(
            predicates,
            arguments,
            predicate,
            value == null
                ? null
                : value.name()
        );
    }

    private static KafkaDeadLetterCommandAudit mapAudit(
        ResultSet resultSet,
        int rowNumber
    ) throws SQLException {
        return new KafkaDeadLetterCommandAudit(
            resultSet.getObject(
                "id",
                UUID.class
            ),
            resultSet.getObject(
                "command_id",
                UUID.class
            ),
            KafkaDeadLetterCommandAuditStage.valueOf(
                resultSet.getString(
                    "stage"
                )
            ),
            resultSet.getObject(
                "operator_id",
                UUID.class
            ),
            resultSet.getObject(
                "dead_letter_record_id",
                UUID.class
            ),
            KafkaDeadLetterCommandType.valueOf(
                resultSet.getString(
                    "command_type"
                )
            ),
            nullableOutcome(
                resultSet.getString(
                    "outcome"
                )
            ),
            resultSet.getString(
                "error_code"
            ),
            instant(
                resultSet,
                "occurred_at"
            )
        );
    }

    private static KafkaDeadLetterCommandAuditOutcome
    nullableOutcome(
        String value
    ) {
        return value == null
            ? null
            : KafkaDeadLetterCommandAuditOutcome
                .valueOf(value);
    }

    private static Instant instant(
        ResultSet resultSet,
        String columnName
    ) throws SQLException {
        Timestamp timestamp =
            resultSet.getTimestamp(columnName);

        return Objects.requireNonNull(
            timestamp,
            columnName + " must not be null"
        ).toInstant();
    }

    private static void validatePage(
        int page
    ) {
        if (page < 0) {
            throw new IllegalArgumentException(
                "page must not be negative"
            );
        }
    }

    private static void validateSize(
        int size
    ) {
        if (size < 1) {
            throw new IllegalArgumentException(
                "size must be greater than zero"
            );
        }

    }

    private static long calculateOffset(
        int page,
        int size
    ) {
        return Math.multiplyExact(
            (long) page,
            size
        );
    }

    private static int calculateTotalPages(
        long totalElements,
        int size
    ) {
        if (totalElements == 0) {
            return 0;
        }

        long totalPages =
            Math.floorDiv(
                totalElements - 1,
                size
            ) + 1;

        try {
            return Math.toIntExact(totalPages);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException(
                "Kafka dead-letter command audit "
                    + "page count exceeds the "
                    + "supported range.",
                exception
            );
        }
    }

    private record QueryCriteria(
        String whereClause,
        List<Object> arguments
    ) {
        private QueryCriteria {
            whereClause =
                Objects.requireNonNull(
                    whereClause,
                    "whereClause must not be null"
                );

            arguments =
                List.copyOf(
                    Objects.requireNonNull(
                        arguments,
                        "arguments must not be null"
                    )
                );
        }
    }
}

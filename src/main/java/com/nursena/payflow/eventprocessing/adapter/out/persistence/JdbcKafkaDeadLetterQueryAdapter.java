package com.nursena.payflow.eventprocessing.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterRecordDetails;
import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterRecordFilter;
import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterRecordPage;
import com.nursena.payflow.eventprocessing.application.model.KafkaDeadLetterRecordSummary;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterQueryPort;
import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecordStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcKafkaDeadLetterQueryAdapter
    implements KafkaDeadLetterQueryPort {

    private static final String SUMMARY_SELECT = """
        SELECT
            id,
            status,
            dlt_topic,
            dlt_partition,
            dlt_offset,
            original_topic,
            original_partition,
            original_offset,
            original_consumer_group,
            exception_type,
            replay_count,
            replay_attempt_base,
            received_at,
            last_replayed_at,
            replay_origin_id,
            (
                payload IS NOT NULL
                AND btrim(payload) <> ''
            ) AS payload_available
        FROM kafka_dead_letter_records
        """;

    private static final String LIST_ALL_SQL =
        SUMMARY_SELECT + """
        ORDER BY
            received_at DESC,
            id DESC
        LIMIT ?
        OFFSET ?
        """;

    private static final String LIST_BY_STATUS_SQL =
        SUMMARY_SELECT + """
        WHERE status = ?
        ORDER BY
            received_at DESC,
            id DESC
        LIMIT ?
        OFFSET ?
        """;

    private static final String COUNT_ALL_SQL = """
        SELECT COUNT(*)
        FROM kafka_dead_letter_records
        """;

    private static final String
        COUNT_BY_STATUS_SQL = """
        SELECT COUNT(*)
        FROM kafka_dead_letter_records
        WHERE status = ?
        """;

    private static final String FIND_BY_ID_SQL = """
        SELECT
            id,
            status,
            dlt_topic,
            dlt_partition,
            dlt_offset,
            original_topic,
            original_partition,
            original_offset,
            original_consumer_group,
            exception_type,
            replay_count,
            replay_attempt_base,
            received_at,
            last_replayed_at,
            replay_origin_id,
            (
                payload IS NOT NULL
                AND btrim(payload) <> ''
            ) AS payload_available,
            exception_message,
            last_replay_error,
            replay_lease_until
        FROM kafka_dead_letter_records
        WHERE id = ?
        """;

    private final JdbcTemplate jdbcTemplate;

    JdbcKafkaDeadLetterQueryAdapter(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            Objects.requireNonNull(
                jdbcTemplate,
                "jdbcTemplate must not be null"
            );
    }

    @Override
    public KafkaDeadLetterRecordPage findPage(
        int page,
        int size,
        KafkaDeadLetterRecordFilter filter
    ) {
        validatePage(page);
        validateSize(size);

        KafkaDeadLetterRecordFilter
            validatedFilter =
            Objects.requireNonNull(
                filter,
                "filter must not be null"
            );

        long totalElements =
            countRecords(validatedFilter);

        List<KafkaDeadLetterRecordSummary> items =
            totalElements == 0
                ? List.of()
                : findRecords(
                page,
                size,
                validatedFilter
            );

        return new KafkaDeadLetterRecordPage(
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
    public Optional<KafkaDeadLetterRecordDetails>
    findById(
        UUID recordId
    ) {
        UUID validatedRecordId =
            Objects.requireNonNull(
                recordId,
                "recordId must not be null"
            );

        List<KafkaDeadLetterRecordDetails> records =
            jdbcTemplate.query(
                FIND_BY_ID_SQL,
                JdbcKafkaDeadLetterQueryAdapter
                    ::mapDetails,
                validatedRecordId
            );

        if (records.size() > 1) {
            throw new IllegalStateException(
                "Kafka dead-letter query returned "
                    + records.size()
                    + " records for identifier "
                    + validatedRecordId
                    + "."
            );
        }

        return records.stream()
            .findFirst();
    }

    private List<KafkaDeadLetterRecordSummary>
    findRecords(
        int page,
        int size,
        KafkaDeadLetterRecordFilter filter
    ) {
        long offset =
            calculateOffset(
                page,
                size
            );

        if (filter.status() == null) {
            return jdbcTemplate.query(
                LIST_ALL_SQL,
                JdbcKafkaDeadLetterQueryAdapter
                    ::mapSummary,
                size,
                offset
            );
        }

        return jdbcTemplate.query(
            LIST_BY_STATUS_SQL,
            JdbcKafkaDeadLetterQueryAdapter
                ::mapSummary,
            filter.status().name(),
            size,
            offset
        );
    }

    private long countRecords(
        KafkaDeadLetterRecordFilter filter
    ) {
        Long result;

        if (filter.status() == null) {
            result =
                jdbcTemplate.queryForObject(
                    COUNT_ALL_SQL,
                    Long.class
                );
        } else {
            result =
                jdbcTemplate.queryForObject(
                    COUNT_BY_STATUS_SQL,
                    Long.class,
                    filter.status().name()
                );
        }

        return Objects.requireNonNull(
            result,
            "Kafka dead-letter count "
                + "must not be null"
        );
    }

    private static KafkaDeadLetterRecordSummary
    mapSummary(
        ResultSet resultSet,
        int rowNumber
    ) throws SQLException {

        return new KafkaDeadLetterRecordSummary(
            resultSet.getObject(
                "id",
                UUID.class
            ),
            KafkaDeadLetterRecordStatus.valueOf(
                resultSet.getString(
                    "status"
                )
            ),
            resultSet.getString(
                "dlt_topic"
            ),
            resultSet.getInt(
                "dlt_partition"
            ),
            resultSet.getLong(
                "dlt_offset"
            ),
            resultSet.getString(
                "original_topic"
            ),
            resultSet.getInt(
                "original_partition"
            ),
            resultSet.getLong(
                "original_offset"
            ),
            resultSet.getString(
                "original_consumer_group"
            ),
            resultSet.getString(
                "exception_type"
            ),
            resultSet.getInt(
                "replay_count"
            ),
            resultSet.getInt(
                "replay_attempt_base"
            ),
            instant(
                resultSet,
                "received_at"
            ),
            instant(
                resultSet,
                "last_replayed_at"
            ),
            resultSet.getObject(
                "replay_origin_id",
                UUID.class
            ),
            resultSet.getBoolean(
                "payload_available"
            )
        );
    }

    private static KafkaDeadLetterRecordDetails
    mapDetails(
        ResultSet resultSet,
        int rowNumber
    ) throws SQLException {

        return new KafkaDeadLetterRecordDetails(
            mapSummary(
                resultSet,
                rowNumber
            ),
            resultSet.getString(
                "exception_message"
            ),
            resultSet.getString(
                "last_replay_error"
            ),
            instant(
                resultSet,
                "replay_lease_until"
            )
        );
    }

    private static Instant instant(
        ResultSet resultSet,
        String columnName
    ) throws SQLException {

        Timestamp timestamp =
            resultSet.getTimestamp(
                columnName
            );

        return timestamp == null
            ? null
            : timestamp.toInstant();
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
            return Math.toIntExact(
                totalPages
            );
        } catch (ArithmeticException exception) {
            throw new IllegalStateException(
                "Kafka dead-letter page count "
                    + "exceeds the supported range.",
                exception
            );
        }
    }
}

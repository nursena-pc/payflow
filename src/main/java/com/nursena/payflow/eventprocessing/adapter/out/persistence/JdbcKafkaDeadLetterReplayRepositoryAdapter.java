package com.nursena.payflow.eventprocessing.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterReplayRepositoryPort;
import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecord;
import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecordStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcKafkaDeadLetterReplayRepositoryAdapter
    implements KafkaDeadLetterReplayRepositoryPort {

    private static final int
        MAX_WORKER_ID_LENGTH = 200;

    private static final String CLAIM_SQL = """
        UPDATE kafka_dead_letter_records
        SET
            status = 'REPLAYING',
            replay_count = replay_count + 1,
            last_replayed_at = ?,
            replay_lease_owner = ?,
            replay_lease_until = ?,
            last_replay_error = NULL
        WHERE id = ?
          AND (
                replay_attempt_base::BIGINT
                + replay_count::BIGINT
              ) < ?
          AND payload IS NOT NULL
          AND btrim(payload) <> ''
          AND original_topic <> dlt_topic
          AND (
                status IN (
                    'RECEIVED',
                    'REPLAY_FAILED'
                )
                OR (
                    status = 'REPLAYING'
                    AND replay_lease_until <= ?
                )
          )
        RETURNING
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
            last_replay_error,
            replay_origin_id,
            replay_attempt_base
        """;

    private final JdbcTemplate jdbcTemplate;

    JdbcKafkaDeadLetterReplayRepositoryAdapter(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            Objects.requireNonNull(
                jdbcTemplate,
                "jdbcTemplate must not be null"
            );
    }

    @Override
    public Optional<KafkaDeadLetterRecord>
    tryClaim(
        UUID recordId,
        String workerId,
        Instant claimedAt,
        Duration leaseDuration,
        int maxReplayAttempts
    ) {
        UUID validatedRecordId =
            Objects.requireNonNull(
                recordId,
                "recordId must not be null"
            );

        String validatedWorkerId =
            validateWorkerId(workerId);

        Instant validatedClaimedAt =
            Objects.requireNonNull(
                claimedAt,
                "claimedAt must not be null"
            );

        Duration validatedLeaseDuration =
            validateLeaseDuration(
                leaseDuration
            );

        if (maxReplayAttempts <= 0) {
            throw new IllegalArgumentException(
                "maxReplayAttempts must be "
                    + "positive."
            );
        }

        Instant leaseUntil =
            calculateLeaseUntil(
                validatedClaimedAt,
                validatedLeaseDuration
            );

        List<KafkaDeadLetterRecord> claimed =
            jdbcTemplate.query(
                CLAIM_SQL,
                JdbcKafkaDeadLetterReplayRepositoryAdapter
                    ::mapRecord,
                Timestamp.from(
                    validatedClaimedAt
                ),
                validatedWorkerId,
                Timestamp.from(leaseUntil),
                validatedRecordId,
                maxReplayAttempts,
                Timestamp.from(
                    validatedClaimedAt
                )
            );

        if (claimed.size() > 1) {
            throw new IllegalStateException(
                "Kafka dead-letter claim returned "
                    + claimed.size()
                    + " records."
            );
        }

        return claimed.stream()
            .findFirst();
    }

    private static KafkaDeadLetterRecord
    mapRecord(
        ResultSet resultSet,
        int rowNumber
    ) throws SQLException {

        return new KafkaDeadLetterRecord(
            resultSet.getObject(
                "id",
                UUID.class
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
                "record_key"
            ),
            resultSet.getString(
                "payload"
            ),
            resultSet.getString(
                "exception_type"
            ),
            resultSet.getString(
                "exception_message"
            ),
            KafkaDeadLetterRecordStatus.valueOf(
                resultSet.getString(
                    "status"
                )
            ),
            resultSet.getInt(
                "replay_count"
            ),
            instant(
                resultSet,
                "received_at"
            ),
            instant(
                resultSet,
                "last_replayed_at"
            ),
            resultSet.getString(
                "replay_lease_owner"
            ),
            instant(
                resultSet,
                "replay_lease_until"
            ),
            resultSet.getString(
                "last_replay_error"
            ),
            resultSet.getObject(
                "replay_origin_id",
                UUID.class
            ),
            resultSet.getInt(
                "replay_attempt_base"
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

    private static String validateWorkerId(
        String value
    ) {
        if (
            value == null
                || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                "workerId must not be blank."
            );
        }

        if (
            value.length()
                > MAX_WORKER_ID_LENGTH
        ) {
            throw new IllegalArgumentException(
                "workerId must not exceed "
                    + MAX_WORKER_ID_LENGTH
                    + " characters."
            );
        }

        return value;
    }

    private static Duration
    validateLeaseDuration(
        Duration value
    ) {
        Objects.requireNonNull(
            value,
            "leaseDuration must not be null"
        );

        if (
            value.isZero()
                || value.isNegative()
        ) {
            throw new IllegalArgumentException(
                "leaseDuration must be positive."
            );
        }

        return value;
    }

    private static Instant calculateLeaseUntil(
        Instant claimedAt,
        Duration leaseDuration
    ) {
        try {
            return claimedAt.plus(
                leaseDuration
            );
        } catch (
            DateTimeException
            | ArithmeticException exception
        ) {
            throw new IllegalArgumentException(
                "leaseDuration produces "
                    + "an invalid lease end.",
                exception
            );
        }
    }
}

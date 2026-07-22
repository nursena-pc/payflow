package com.nursena.payflow.eventprocessing.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.eventprocessing.application.model.ClaimKafkaDeadLetterRecordResult;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterReplayLifecyclePort;
import com.nursena.payflow.eventprocessing.application.port.out.KafkaDeadLetterReplayRepositoryPort;
import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecord;
import com.nursena.payflow.eventprocessing.domain.model.KafkaDeadLetterRecordStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class JdbcKafkaDeadLetterReplayRepositoryAdapter
    implements
    KafkaDeadLetterReplayRepositoryPort,
    KafkaDeadLetterReplayLifecyclePort {

    private static final int
        MAX_WORKER_ID_LENGTH = 200;

    private static final String CLAIM_SQL = """
    WITH target AS MATERIALIZED (
        SELECT id
        FROM kafka_dead_letter_records
        WHERE id = ?
        FOR UPDATE
    ),
    claimed AS (
        UPDATE kafka_dead_letter_records
            AS record
        SET
            status = 'REPLAYING',
            replay_count =
                record.replay_count + 1,
            last_replayed_at = ?,
            replay_lease_owner = ?,
            replay_lease_until = ?,
            last_replay_error = NULL
        FROM target
        WHERE record.id = target.id
          AND (
                record.replay_attempt_base::BIGINT
                + record.replay_count::BIGINT
              ) < ?
          AND record.payload IS NOT NULL
          AND btrim(record.payload) <> ''
          AND record.original_topic
                <> record.dlt_topic
          AND (
                record.status IN (
                    'RECEIVED',
                    'REPLAY_FAILED'
                )
                OR (
                    record.status = 'REPLAYING'
                    AND record.replay_lease_until <= ?
                )
          )
        RETURNING
            record.id,
            record.dlt_topic,
            record.dlt_partition,
            record.dlt_offset,
            record.original_topic,
            record.original_partition,
            record.original_offset,
            record.original_consumer_group,
            record.record_key,
            record.payload,
            record.exception_type,
            record.exception_message,
            record.status,
            record.replay_count,
            record.received_at,
            record.last_replayed_at,
            record.replay_lease_owner,
            record.replay_lease_until,
            record.last_replay_error,
            record.replay_origin_id,
            record.replay_attempt_base
    )
    SELECT
        CASE
            WHEN claimed.id IS NOT NULL
                THEN 'CLAIMED'
            WHEN EXISTS (
                SELECT 1
                FROM target
            )
                THEN 'NOT_CLAIMABLE'
            ELSE 'NOT_FOUND'
        END AS claim_outcome,
        claimed.id,
        claimed.dlt_topic,
        claimed.dlt_partition,
        claimed.dlt_offset,
        claimed.original_topic,
        claimed.original_partition,
        claimed.original_offset,
        claimed.original_consumer_group,
        claimed.record_key,
        claimed.payload,
        claimed.exception_type,
        claimed.exception_message,
        claimed.status,
        claimed.replay_count,
        claimed.received_at,
        claimed.last_replayed_at,
        claimed.replay_lease_owner,
        claimed.replay_lease_until,
        claimed.last_replay_error,
        claimed.replay_origin_id,
        claimed.replay_attempt_base
    FROM (
        VALUES (1)
    ) AS anchor(value)
    LEFT JOIN claimed
        ON TRUE
    """;

    private static final String
        MARK_REPLAYED_SQL = """
        UPDATE kafka_dead_letter_records
        SET
            status = 'REPLAYED',
            replay_lease_owner = NULL,
            replay_lease_until = NULL,
            last_replay_error = NULL
        WHERE id = ?
          AND status = 'REPLAYING'
          AND replay_lease_owner = ?
          AND last_replayed_at <= ?
          AND replay_lease_until > ?
        """;

    private static final String
        MARK_REPLAY_FAILED_SQL = """
        UPDATE kafka_dead_letter_records
        SET
            status = 'REPLAY_FAILED',
            replay_lease_owner = NULL,
            replay_lease_until = NULL,
            last_replay_error = ?
        WHERE id = ?
          AND status = 'REPLAYING'
          AND replay_lease_owner = ?
          AND last_replayed_at <= ?
          AND replay_lease_until > ?
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
    public ClaimKafkaDeadLetterRecordResult
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

        ClaimKafkaDeadLetterRecordResult result =
            jdbcTemplate.queryForObject(
                CLAIM_SQL,
                JdbcKafkaDeadLetterReplayRepositoryAdapter
                    ::mapClaimResult,
                validatedRecordId,
                Timestamp.from(
                    validatedClaimedAt
                ),
                validatedWorkerId,
                Timestamp.from(leaseUntil),
                maxReplayAttempts,
                Timestamp.from(
                    validatedClaimedAt
                )
            );

        return Objects.requireNonNull(
            result,
            "Kafka dead-letter claim result "
                + "must not be null"
        );
    }

    @Override
    public boolean tryMarkReplayed(
        UUID recordId,
        String workerId,
        Instant completedAt
    ) {
        UUID validatedRecordId =
            Objects.requireNonNull(
                recordId,
                "recordId must not be null"
            );

        String validatedWorkerId =
            validateWorkerId(workerId);

        Instant validatedCompletedAt =
            Objects.requireNonNull(
                completedAt,
                "completedAt must not be null"
            );

        int affectedRows =
            jdbcTemplate.update(
                MARK_REPLAYED_SQL,
                validatedRecordId,
                validatedWorkerId,
                Timestamp.from(
                    validatedCompletedAt
                ),
                Timestamp.from(
                    validatedCompletedAt
                )
            );

        return transitionResult(
            affectedRows,
            "mark replayed"
        );
    }

    @Override
    public boolean tryMarkReplayFailed(
        UUID recordId,
        String workerId,
        Instant failedAt,
        String error
    ) {
        UUID validatedRecordId =
            Objects.requireNonNull(
                recordId,
                "recordId must not be null"
            );

        String validatedWorkerId =
            validateWorkerId(workerId);

        Instant validatedFailedAt =
            Objects.requireNonNull(
                failedAt,
                "failedAt must not be null"
            );

        String validatedError =
            validateReplayError(error);

        int affectedRows =
            jdbcTemplate.update(
                MARK_REPLAY_FAILED_SQL,
                validatedError,
                validatedRecordId,
                validatedWorkerId,
                Timestamp.from(
                    validatedFailedAt
                ),
                Timestamp.from(
                    validatedFailedAt
                )
            );

        return transitionResult(
            affectedRows,
            "mark replay failed"
        );
    }
    private static ClaimKafkaDeadLetterRecordResult
    mapClaimResult(
        ResultSet resultSet,
        int rowNumber
    ) throws SQLException {

        String outcome =
            resultSet.getString(
                "claim_outcome"
            );

        return switch (outcome) {
            case "CLAIMED" ->
                ClaimKafkaDeadLetterRecordResult
                    .claimed(
                        mapRecord(
                            resultSet,
                            rowNumber
                        )
                    );

            case "NOT_FOUND" ->
                ClaimKafkaDeadLetterRecordResult
                    .notFound();

            case "NOT_CLAIMABLE" ->
                ClaimKafkaDeadLetterRecordResult
                    .notClaimable();

            default ->
                throw new SQLException(
                    "Unknown Kafka dead-letter "
                        + "claim outcome: "
                        + outcome
                );
        };
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

    private static String validateReplayError(
        String value
    ) {
        if (
            value == null
                || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                "error must not be blank."
            );
        }

        return value;
    }

    private static boolean transitionResult(
        int affectedRows,
        String operation
    ) {
        if (affectedRows == 1) {
            return true;
        }

        if (affectedRows == 0) {
            return false;
        }

        throw new IllegalStateException(
            "Kafka dead-letter replay "
                + operation
                + " operation affected "
                + affectedRows
                + " rows."
        );
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

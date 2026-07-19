package com.nursena.payflow.outbox.adapter.out.persistence;

import java.sql.Timestamp;
import java.util.Objects;
import java.util.Optional;

import com.nursena.payflow.outbox.application.model.OutboxBacklogSnapshot;
import com.nursena.payflow.outbox.application.port.out.OutboxBacklogQueryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOutboxBacklogQueryAdapter
    implements OutboxBacklogQueryPort {

    private static final String LOAD_SNAPSHOT_SQL = """
        SELECT
            COUNT(*) AS event_count,
            MIN(created_at) AS oldest_created_at
        FROM outbox_events
        WHERE status IN (
            'PENDING',
            'PROCESSING'
        )
        """;

    private final JdbcTemplate jdbcTemplate;

    JdbcOutboxBacklogQueryAdapter(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate =
            Objects.requireNonNull(
                jdbcTemplate,
                "jdbcTemplate must not be null"
            );
    }

    @Override
    public OutboxBacklogSnapshot loadSnapshot() {
        OutboxBacklogSnapshot snapshot =
            jdbcTemplate.queryForObject(
                LOAD_SNAPSHOT_SQL,
                (resultSet, rowNumber) -> {
                    long eventCount =
                        resultSet.getLong(
                            "event_count"
                        );

                    Timestamp oldestCreatedAt =
                        resultSet.getTimestamp(
                            "oldest_created_at"
                        );

                    return new OutboxBacklogSnapshot(
                        eventCount,
                        Optional.ofNullable(
                            oldestCreatedAt
                        ).map(
                            Timestamp::toInstant
                        )
                    );
                }
            );

        return Objects.requireNonNull(
            snapshot,
            "Backlog query must return a snapshot."
        );
    }
}

package com.nursena.payflow.eventprocessing.adapter.out.persistence;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.nursena.payflow.eventprocessing.application.port.out.TransferCompletedEventHandlerPort;
import com.nursena.payflow.transaction.application.model.TransferCompletedEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTransferCompletedEventAuditHandlerAdapter
    implements TransferCompletedEventHandlerPort {

    private static final String INSERT_SQL = """
        INSERT INTO transfer_completed_event_audits (
            event_id,
            event_type,
            event_version,
            occurred_at,
            transaction_id,
            source_wallet_id,
            target_wallet_id,
            amount,
            currency,
            recorded_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    private final JdbcTemplate jdbcTemplate;

    private final Clock clock;

    JdbcTransferCompletedEventAuditHandlerAdapter(
        JdbcTemplate jdbcTemplate,
        Clock clock
    ) {
        this.jdbcTemplate =
            Objects.requireNonNull(
                jdbcTemplate,
                "jdbcTemplate must not be null"
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );
    }

    @Override
    public void handle(
        TransferCompletedEvent event
    ) {
        Objects.requireNonNull(
            event,
            "event must not be null"
        );

        int affectedRows = jdbcTemplate.update(
            INSERT_SQL,
            event.eventId(),
            event.eventType(),
            event.eventVersion(),
            Timestamp.from(
                event.occurredAt()
            ),
            event.transactionId(),
            event.sourceWalletId(),
            event.targetWalletId(),
            parseAmount(
                event.amount()
            ),
            event.currency(),
            Timestamp.from(
                currentTime()
            )
        );

        if (affectedRows != 1) {
            throw new IllegalStateException(
                "Transfer completed audit insert "
                    + "affected "
                    + affectedRows
                    + " rows."
            );
        }
    }

    private Instant currentTime() {
        return clock
            .instant()
            .truncatedTo(
                ChronoUnit.MICROS
            );
    }

    private static BigDecimal parseAmount(
        String amount
    ) {
        try {
            return new BigDecimal(amount);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                "amount must be a valid decimal.",
                exception
            );
        }
    }
}

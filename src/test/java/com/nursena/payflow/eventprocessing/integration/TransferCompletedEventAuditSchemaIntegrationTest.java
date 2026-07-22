package com.nursena.payflow.eventprocessing.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class TransferCompletedEventAuditSchemaIntegrationTest {

    private static final UUID EVENT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000000801"
        );

    private static final UUID SECOND_EVENT_ID =
        UUID.fromString(
            "50000000-0000-0000-0000-000000000802"
        );

    private static final UUID TRANSACTION_ID =
        UUID.fromString(
            "60000000-0000-0000-0000-000000000801"
        );

    private static final UUID SOURCE_WALLET_ID =
        UUID.fromString(
            "70000000-0000-0000-0000-000000000801"
        );

    private static final UUID TARGET_WALLET_ID =
        UUID.fromString(
            "70000000-0000-0000-0000-000000000802"
        );

    private static final Instant OCCURRED_AT =
        Instant.parse(
            "2026-07-20T20:00:00Z"
        );

    private static final Instant RECORDED_AT =
        Instant.parse(
            "2026-07-20T20:00:01Z"
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
        jdbcTemplate.update(
            "DELETE FROM transfer_completed_event_audits"
        );
    }

    @Test
    void shouldPersistValidAuditRecord() {
        insertAudit(
            EVENT_ID,
            TRANSACTION_ID,
            "wallet.transfer.completed",
            1,
            SOURCE_WALLET_ID,
            TARGET_WALLET_ID,
            new BigDecimal("125.50"),
            "TRY"
        );

        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM transfer_completed_event_audits
            WHERE event_id = ?
            """,
            Integer.class,
            EVENT_ID
        );

        assertThat(count)
            .isEqualTo(1);
    }

    @Test
    void shouldRejectDuplicateTransactionId() {
        insertAudit(
            EVENT_ID,
            TRANSACTION_ID,
            "wallet.transfer.completed",
            1,
            SOURCE_WALLET_ID,
            TARGET_WALLET_ID,
            new BigDecimal("125.50"),
            "TRY"
        );

        assertConstraintViolation(
            () -> insertAudit(
                SECOND_EVENT_ID,
                TRANSACTION_ID,
                "wallet.transfer.completed",
                1,
                SOURCE_WALLET_ID,
                TARGET_WALLET_ID,
                new BigDecimal("125.50"),
                "TRY"
            ),
            "uq_transfer_completed_event_audits_transaction_id"
        );
    }

    @Test
    void shouldRejectUnsupportedEventType() {
        assertConstraintViolation(
            () -> insertAudit(
                EVENT_ID,
                TRANSACTION_ID,
                "wallet.transfer.failed",
                1,
                SOURCE_WALLET_ID,
                TARGET_WALLET_ID,
                new BigDecimal("125.50"),
                "TRY"
            ),
            "chk_transfer_completed_event_audits_type"
        );
    }

    @Test
    void shouldRejectUnsupportedEventVersion() {
        assertConstraintViolation(
            () -> insertAudit(
                EVENT_ID,
                TRANSACTION_ID,
                "wallet.transfer.completed",
                2,
                SOURCE_WALLET_ID,
                TARGET_WALLET_ID,
                new BigDecimal("125.50"),
                "TRY"
            ),
            "chk_transfer_completed_event_audits_version"
        );
    }

    @Test
    void shouldRejectSameSourceAndTargetWallet() {
        assertConstraintViolation(
            () -> insertAudit(
                EVENT_ID,
                TRANSACTION_ID,
                "wallet.transfer.completed",
                1,
                SOURCE_WALLET_ID,
                SOURCE_WALLET_ID,
                new BigDecimal("125.50"),
                "TRY"
            ),
            "chk_transfer_completed_event_audits_wallets"
        );
    }

    @Test
    void shouldRejectNonPositiveAmount() {
        assertConstraintViolation(
            () -> insertAudit(
                EVENT_ID,
                TRANSACTION_ID,
                "wallet.transfer.completed",
                1,
                SOURCE_WALLET_ID,
                TARGET_WALLET_ID,
                BigDecimal.ZERO,
                "TRY"
            ),
            "chk_transfer_completed_event_audits_amount"
        );
    }

    @Test
    void shouldRejectInvalidCurrency() {
        assertConstraintViolation(
            () -> insertAudit(
                EVENT_ID,
                TRANSACTION_ID,
                "wallet.transfer.completed",
                1,
                SOURCE_WALLET_ID,
                TARGET_WALLET_ID,
                new BigDecimal("125.50"),
                "tr"
            ),
            "chk_transfer_completed_event_audits_currency"
        );
    }

    private void insertAudit(
        UUID eventId,
        UUID transactionId,
        String eventType,
        int eventVersion,
        UUID sourceWalletId,
        UUID targetWalletId,
        BigDecimal amount,
        String currency
    ) {
        jdbcTemplate.update(
            """
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
            """,
            eventId,
            eventType,
            eventVersion,
            Timestamp.from(OCCURRED_AT),
            transactionId,
            sourceWalletId,
            targetWalletId,
            amount,
            currency,
            Timestamp.from(RECORDED_AT)
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

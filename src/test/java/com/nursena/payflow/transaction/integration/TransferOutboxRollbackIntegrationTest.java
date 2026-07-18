package com.nursena.payflow.transaction.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.outbox.application.port.out.OutboxEventRepositoryPort;
import com.nursena.payflow.outbox.domain.model.OutboxEvent;
import com.nursena.payflow.outbox.domain.model.OutboxStatus;
import com.nursena.payflow.transaction.application.model.TransferCompletedEvent;
import com.nursena.payflow.transaction.application.port.in.TransferMoneyCommand;
import com.nursena.payflow.transaction.application.port.in.TransferMoneyUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class TransferOutboxRollbackIntegrationTest {

    private static final UUID SOURCE_OWNER_ID =
        UUID.fromString(
            "81000000-0000-0000-0000-000000000001"
        );

    private static final UUID TARGET_OWNER_ID =
        UUID.fromString(
            "81000000-0000-0000-0000-000000000002"
        );

    private static final UUID SOURCE_WALLET_ID =
        UUID.fromString(
            "82000000-0000-0000-0000-000000000001"
        );

    private static final UUID TARGET_WALLET_ID =
        UUID.fromString(
            "82000000-0000-0000-0000-000000000002"
        );

    private static final BigDecimal
        INITIAL_SOURCE_BALANCE =
        new BigDecimal("300.00");

    private static final BigDecimal TRANSFER_AMOUNT =
        new BigDecimal("125.50");

    private static final String IDEMPOTENCY_KEY =
        "outbox-rollback-request-1";

    private static final Instant BASE_TIME =
        Instant.parse(
            "2026-07-18T10:00:00Z"
        );

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
            "postgres:17-alpine"
        );

    @Autowired
    private TransferMoneyUseCase transferMoneyUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private OutboxEventRepositoryPort
        outboxEventRepository;

    @BeforeEach
    void setUpDatabase() {
        jdbcTemplate.update(
            "DELETE FROM outbox_events"
        );

        jdbcTemplate.update(
            "DELETE FROM ledger_entries"
        );

        jdbcTemplate.update(
            "DELETE FROM payment_transactions"
        );

        jdbcTemplate.update(
            "DELETE FROM wallets"
        );

        jdbcTemplate.update(
            "DELETE FROM users"
        );

        insertUser(
            SOURCE_OWNER_ID,
            "outbox-rollback-source@example.com"
        );

        insertUser(
            TARGET_OWNER_ID,
            "outbox-rollback-target@example.com"
        );

        insertWallet(
            SOURCE_WALLET_ID,
            SOURCE_OWNER_ID,
            INITIAL_SOURCE_BALANCE,
            1L
        );

        insertWallet(
            TARGET_WALLET_ID,
            TARGET_OWNER_ID,
            BigDecimal.ZERO,
            0L
        );
    }

    @Test
    void shouldRollbackEntireTransferWhenOutboxPersistenceFails() {
        when(outboxEventRepository.save(
            any(OutboxEvent.class)
        )).thenThrow(
            new IllegalStateException(
                "Simulated outbox persistence failure."
            )
        );

        TransferMoneyCommand command =
            new TransferMoneyCommand(
                SOURCE_OWNER_ID,
                TARGET_WALLET_ID,
                TRANSFER_AMOUNT,
                IDEMPOTENCY_KEY
            );

        assertThatThrownBy(() ->
            transferMoneyUseCase.transfer(command)
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessage(
                "Simulated outbox persistence failure."
            );

        ArgumentCaptor<OutboxEvent> eventCaptor =
            ArgumentCaptor.forClass(
                OutboxEvent.class
            );

        verify(outboxEventRepository)
            .save(eventCaptor.capture());

        OutboxEvent attemptedEvent =
            eventCaptor.getValue();

        assertThat(attemptedEvent.aggregateType())
            .isEqualTo(
                "PAYMENT_TRANSACTION"
            );

        assertThat(attemptedEvent.eventType())
            .isEqualTo(
                TransferCompletedEvent.TYPE
            );

        assertThat(attemptedEvent.eventVersion())
            .isEqualTo(
                TransferCompletedEvent.VERSION
            );

        assertThat(attemptedEvent.status())
            .isEqualTo(
                OutboxStatus.PENDING
            );

        assertThat(
            attemptedEvent.partitionKey()
        ).isEqualTo(
            attemptedEvent
                .aggregateId()
                .toString()
        );

        assertThat(
            attemptedEvent.deduplicationKey()
        ).isEqualTo(
            "wallet.transfer.completed:1:"
                + attemptedEvent.aggregateId()
        );

        assertThat(
            walletBalance(SOURCE_WALLET_ID)
        ).isEqualByComparingTo(
            INITIAL_SOURCE_BALANCE
        );

        assertThat(
            walletBalance(TARGET_WALLET_ID)
        ).isEqualByComparingTo(
            BigDecimal.ZERO
        );

        assertThat(
            walletVersion(SOURCE_WALLET_ID)
        ).isEqualTo(1L);

        assertThat(
            walletVersion(TARGET_WALLET_ID)
        ).isEqualTo(0L);

        Long transactionCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM payment_transactions
                WHERE source_wallet_id = ?
                  AND idempotency_key = ?
                """,
                Long.class,
                SOURCE_WALLET_ID,
                IDEMPOTENCY_KEY
            );

        assertThat(transactionCount)
            .isZero();

        Long ledgerEntryCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM ledger_entries
                WHERE wallet_id IN (?, ?)
                """,
                Long.class,
                SOURCE_WALLET_ID,
                TARGET_WALLET_ID
            );

        assertThat(ledgerEntryCount)
            .isZero();

        Long outboxEventCount =
            jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM outbox_events
                """,
                Long.class
            );

        assertThat(outboxEventCount)
            .isZero();
    }

    private BigDecimal walletBalance(
        UUID walletId
    ) {
        return jdbcTemplate.queryForObject(
            """
            SELECT balance
            FROM wallets
            WHERE id = ?
            """,
            BigDecimal.class,
            walletId
        );
    }

    private Long walletVersion(
        UUID walletId
    ) {
        return jdbcTemplate.queryForObject(
            """
            SELECT version
            FROM wallets
            WHERE id = ?
            """,
            Long.class,
            walletId
        );
    }

    private void insertUser(
        UUID userId,
        String email
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO users (
                id,
                email,
                password_hash,
                role,
                status,
                created_at,
                updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            userId,
            email,
            "test-password-hash",
            "USER",
            "ACTIVE",
            Timestamp.from(BASE_TIME),
            Timestamp.from(BASE_TIME)
        );
    }

    private void insertWallet(
        UUID walletId,
        UUID ownerId,
        BigDecimal balance,
        long version
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO wallets (
                id,
                owner_id,
                balance,
                currency,
                status,
                version,
                created_at,
                updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            walletId,
            ownerId,
            balance,
            "TRY",
            "ACTIVE",
            version,
            Timestamp.from(BASE_TIME),
            Timestamp.from(BASE_TIME)
        );
    }
}

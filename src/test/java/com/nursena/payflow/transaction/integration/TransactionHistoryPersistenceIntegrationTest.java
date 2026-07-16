package com.nursena.payflow.transaction.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.transaction.application.model.TransactionDirection;
import com.nursena.payflow.transaction.application.model.TransactionHistoryPage;
import com.nursena.payflow.transaction.application.port.out.TransactionHistoryQueryPort;
import com.nursena.payflow.transaction.application.model.TransactionHistoryFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class TransactionHistoryPersistenceIntegrationTest {

    private static final UUID OWNER_ID =
        UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
        );

    private static final UUID COUNTERPARTY_OWNER_A_ID =
        UUID.fromString(
            "10000000-0000-0000-0000-000000000002"
        );

    private static final UUID COUNTERPARTY_OWNER_B_ID =
        UUID.fromString(
            "10000000-0000-0000-0000-000000000003"
        );

    private static final UUID WALLET_ID =
        UUID.fromString(
            "20000000-0000-0000-0000-000000000001"
        );

    private static final UUID COUNTERPARTY_WALLET_A_ID =
        UUID.fromString(
            "20000000-0000-0000-0000-000000000002"
        );

    private static final UUID COUNTERPARTY_WALLET_B_ID =
        UUID.fromString(
            "20000000-0000-0000-0000-000000000003"
        );

    private static final UUID NEWEST_TRANSACTION_ID =
        UUID.fromString(
            "30000000-0000-0000-0000-000000000003"
        );

    private static final UUID SAME_TIME_HIGH_ID =
        UUID.fromString(
            "ffffffff-ffff-ffff-ffff-ffffffffffff"
        );

    private static final UUID SAME_TIME_LOW_ID =
        UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
        );

    private static final UUID UNRELATED_TRANSACTION_ID =
        UUID.fromString(
            "40000000-0000-0000-0000-000000000001"
        );

    private static final Instant BASE_TIME =
        Instant.parse(
            "2026-07-16T10:00:00Z"
        );

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(
            "postgres:17-alpine"
        );

    @Autowired
    private TransactionHistoryQueryPort
        transactionHistoryQueryPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
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
    }

    @Test
    void shouldReturnIsolatedDeterministicallyOrderedHistory() {
        insertUser(
            OWNER_ID,
            "history-owner@example.com"
        );

        insertUser(
            COUNTERPARTY_OWNER_A_ID,
            "history-counterparty-a@example.com"
        );

        insertUser(
            COUNTERPARTY_OWNER_B_ID,
            "history-counterparty-b@example.com"
        );

        insertWallet(
            WALLET_ID,
            OWNER_ID
        );

        insertWallet(
            COUNTERPARTY_WALLET_A_ID,
            COUNTERPARTY_OWNER_A_ID
        );

        insertWallet(
            COUNTERPARTY_WALLET_B_ID,
            COUNTERPARTY_OWNER_B_ID
        );

        insertTransaction(
            NEWEST_TRANSACTION_ID,
            COUNTERPARTY_WALLET_A_ID,
            WALLET_ID,
            "history-newest",
            BASE_TIME.plusSeconds(60)
        );

        insertTransaction(
            SAME_TIME_HIGH_ID,
            WALLET_ID,
            COUNTERPARTY_WALLET_B_ID,
            "history-same-high",
            BASE_TIME
        );

        insertTransaction(
            SAME_TIME_LOW_ID,
            COUNTERPARTY_WALLET_B_ID,
            WALLET_ID,
            "history-same-low",
            BASE_TIME
        );

        insertTransaction(
            UNRELATED_TRANSACTION_ID,
            COUNTERPARTY_WALLET_A_ID,
            COUNTERPARTY_WALLET_B_ID,
            "history-unrelated",
            BASE_TIME.plusSeconds(120)
        );

        TransactionHistoryPage firstPage =
            transactionHistoryQueryPort
                .findByWalletId(
                    WALLET_ID,
                    0,
                    2,
                    TransactionHistoryFilter.unfiltered()
                );

        assertThat(firstPage.totalElements())
            .isEqualTo(3);

        assertThat(firstPage.totalPages())
            .isEqualTo(2);

        assertThat(firstPage.page())
            .isZero();

        assertThat(firstPage.size())
            .isEqualTo(2);

        assertThat(firstPage.first())
            .isTrue();

        assertThat(firstPage.last())
            .isFalse();

        assertThat(firstPage.items())
            .extracting(item ->
                item.transactionId()
            )
            .containsExactly(
                NEWEST_TRANSACTION_ID,
                SAME_TIME_HIGH_ID
            );

        assertThat(
            firstPage.items()
                .get(0)
                .direction()
        ).isEqualTo(
            TransactionDirection.INCOMING
        );

        assertThat(
            firstPage.items()
                .get(0)
                .counterpartyWalletId()
        ).isEqualTo(
            COUNTERPARTY_WALLET_A_ID
        );

        assertThat(
            firstPage.items()
                .get(1)
                .direction()
        ).isEqualTo(
            TransactionDirection.OUTGOING
        );

        assertThat(
            firstPage.items()
                .get(1)
                .counterpartyWalletId()
        ).isEqualTo(
            COUNTERPARTY_WALLET_B_ID
        );

        TransactionHistoryPage secondPage =
            transactionHistoryQueryPort
                .findByWalletId(
                    WALLET_ID,
                    1,
                    2,
                    TransactionHistoryFilter.unfiltered()
                );

        assertThat(secondPage.items())
            .extracting(item ->
                item.transactionId()
            )
            .containsExactly(
                SAME_TIME_LOW_ID
            );

        assertThat(secondPage.last())
            .isTrue();

        assertThat(
            secondPage.items()
                .getFirst()
                .direction()
        ).isEqualTo(
            TransactionDirection.INCOMING
        );

        assertThat(
            secondPage.items()
                .getFirst()
                .counterpartyWalletId()
        ).isEqualTo(
            COUNTERPARTY_WALLET_B_ID
        );
        TransactionHistoryPage outgoingPage =
            transactionHistoryQueryPort
                .findByWalletId(
                    WALLET_ID,
                    0,
                    20,
                    new TransactionHistoryFilter(
                        TransactionDirection.OUTGOING,
                        null,
                        null,
                        null
                    )
                );

        assertThat(outgoingPage.items())
            .extracting(item ->
                item.transactionId()
            )
            .containsExactly(
                SAME_TIME_HIGH_ID
            );

        TransactionHistoryPage incomingPage =
            transactionHistoryQueryPort
                .findByWalletId(
                    WALLET_ID,
                    0,
                    20,
                    new TransactionHistoryFilter(
                        TransactionDirection.INCOMING,
                        null,
                        null,
                        null
                    )
                );

        assertThat(incomingPage.items())
            .extracting(item ->
                item.transactionId()
            )
            .containsExactly(
                NEWEST_TRANSACTION_ID,
                SAME_TIME_LOW_ID
            );

        TransactionHistoryPage dateFilteredPage =
            transactionHistoryQueryPort
                .findByWalletId(
                    WALLET_ID,
                    0,
                    20,
                    new TransactionHistoryFilter(
                        null,
                        null,
                        BASE_TIME,
                        BASE_TIME.plusSeconds(60)
                    )
                );

        assertThat(dateFilteredPage.items())
            .extracting(item ->
                item.transactionId()
            )
            .containsExactly(
                SAME_TIME_HIGH_ID,
                SAME_TIME_LOW_ID
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
        UUID ownerId
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
            new BigDecimal("500.00"),
            "TRY",
            "ACTIVE",
            0L,
            Timestamp.from(BASE_TIME),
            Timestamp.from(BASE_TIME)
        );
    }

    private void insertTransaction(
        UUID transactionId,
        UUID sourceWalletId,
        UUID targetWalletId,
        String idempotencyKey,
        Instant createdAt
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO payment_transactions (
                id,
                source_wallet_id,
                target_wallet_id,
                transaction_type,
                status,
                amount,
                currency,
                idempotency_key,
                failure_reason,
                created_at,
                completed_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            transactionId,
            sourceWalletId,
            targetWalletId,
            "TRANSFER",
            "COMPLETED",
            new BigDecimal("125.50"),
            "TRY",
            idempotencyKey,
            null,
            Timestamp.from(createdAt),
            Timestamp.from(
                createdAt.plusSeconds(1)
            )
        );
    }
}

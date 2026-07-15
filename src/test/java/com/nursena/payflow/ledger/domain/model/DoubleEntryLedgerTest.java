package com.nursena.payflow.ledger.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.ledger.domain.exception.InvalidLedgerEntryPairException;
import com.nursena.payflow.wallet.domain.model.Currency;
import com.nursena.payflow.wallet.domain.model.Money;
import org.junit.jupiter.api.Test;

class DoubleEntryLedgerTest {

    private static final UUID TRANSACTION_ID =
        UUID.fromString(
            "b4077781-34f4-466f-8e61-b79ca906bc98"
        );

    private static final UUID SOURCE_WALLET_ID =
        UUID.fromString(
            "8805681d-d537-42f2-8906-5da1f0666ab7"
        );

    private static final UUID TARGET_WALLET_ID =
        UUID.fromString(
            "461ffd4c-29cc-4dbf-82b5-c9af3e1da8db"
        );

    private static final Instant NOW =
        Instant.parse("2026-07-15T15:00:00Z");

    @Test
    void shouldCreateBalancedEntriesForTransfer() {
        DoubleEntryLedger ledger =
            DoubleEntryLedger.forTransfer(
                TRANSACTION_ID,
                SOURCE_WALLET_ID,
                TARGET_WALLET_ID,
                Money.of("125.50", Currency.TRY),
                NOW
            );

        LedgerEntry debit = ledger.debitEntry();
        LedgerEntry credit = ledger.creditEntry();

        assertThat(debit.type())
            .isEqualTo(LedgerEntryType.DEBIT);

        assertThat(debit.walletId())
            .isEqualTo(SOURCE_WALLET_ID);

        assertThat(credit.type())
            .isEqualTo(LedgerEntryType.CREDIT);

        assertThat(credit.walletId())
            .isEqualTo(TARGET_WALLET_ID);

        assertThat(debit.transactionId())
            .isEqualTo(TRANSACTION_ID);

        assertThat(credit.transactionId())
            .isEqualTo(TRANSACTION_ID);

        assertThat(debit.amount())
            .isEqualTo(credit.amount());

        assertThat(debit.createdAt())
            .isEqualTo(NOW);

        assertThat(credit.createdAt())
            .isEqualTo(NOW);
    }

    @Test
    void shouldRejectEntriesForSameWallet() {
        assertThatThrownBy(() ->
            DoubleEntryLedger.forTransfer(
                TRANSACTION_ID,
                SOURCE_WALLET_ID,
                SOURCE_WALLET_ID,
                Money.of("25.00", Currency.TRY),
                NOW
            )
        )
            .isInstanceOf(
                InvalidLedgerEntryPairException.class
            );
    }

    @Test
    void shouldRejectUnbalancedEntryPair() {
        LedgerEntry debit = LedgerEntry.create(
            TRANSACTION_ID,
            SOURCE_WALLET_ID,
            LedgerEntryType.DEBIT,
            Money.of("100.00", Currency.TRY),
            NOW
        );

        LedgerEntry credit = LedgerEntry.create(
            TRANSACTION_ID,
            TARGET_WALLET_ID,
            LedgerEntryType.CREDIT,
            Money.of("99.00", Currency.TRY),
            NOW
        );

        assertThatThrownBy(() ->
            new DoubleEntryLedger(
                debit,
                credit
            )
        )
            .isInstanceOf(
                InvalidLedgerEntryPairException.class
            )
            .hasMessage(
                "Ledger entries must form a balanced "
                    + "debit and credit pair."
            );
    }

    @Test
    void shouldRejectEntriesFromDifferentTransactions() {
        LedgerEntry debit = LedgerEntry.create(
            TRANSACTION_ID,
            SOURCE_WALLET_ID,
            LedgerEntryType.DEBIT,
            Money.of("100.00", Currency.TRY),
            NOW
        );

        LedgerEntry credit = LedgerEntry.create(
            UUID.randomUUID(),
            TARGET_WALLET_ID,
            LedgerEntryType.CREDIT,
            Money.of("100.00", Currency.TRY),
            NOW
        );

        assertThatThrownBy(() ->
            new DoubleEntryLedger(
                debit,
                credit
            )
        )
            .isInstanceOf(
                InvalidLedgerEntryPairException.class
            );
    }
}

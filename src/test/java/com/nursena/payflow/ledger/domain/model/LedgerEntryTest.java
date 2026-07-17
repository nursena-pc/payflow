package com.nursena.payflow.ledger.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.ledger.domain.exception.InvalidLedgerAmountException;
import com.nursena.payflow.wallet.domain.model.Currency;
import com.nursena.payflow.wallet.domain.model.Money;
import org.junit.jupiter.api.Test;

class LedgerEntryTest {

    private static final Instant NOW =
        Instant.parse("2026-07-15T15:00:00Z");

    @Test
    void shouldCreateImmutableDebitEntry() {
        UUID transactionId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();

        LedgerEntry entry = LedgerEntry.create(
            transactionId,
            walletId,
            LedgerEntryType.DEBIT,
            Money.of("125.50", Currency.TRY),
            NOW
        );

        assertThat(entry.id())
            .isNotNull();

        assertThat(entry.transactionId())
            .isEqualTo(transactionId);

        assertThat(entry.walletId())
            .isEqualTo(walletId);

        assertThat(entry.type())
            .isEqualTo(LedgerEntryType.DEBIT);

        assertThat(entry.amount())
            .isEqualTo(
                Money.of("125.50", Currency.TRY)
            );

        assertThat(entry.createdAt())
            .isEqualTo(NOW);
    }

    @Test
    void shouldRejectNonPositiveEntryAmount() {
        assertThatThrownBy(() ->
            LedgerEntry.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                LedgerEntryType.CREDIT,
                Money.of("0.00", Currency.TRY),
                NOW
            )
        )
            .isInstanceOf(
                InvalidLedgerAmountException.class
            )
            .hasMessage(
                "Ledger entry amount must "
                    + "be greater than zero."
            );
    }
}

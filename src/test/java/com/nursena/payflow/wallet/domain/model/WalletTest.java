package com.nursena.payflow.wallet.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.wallet.domain.exception.InsufficientBalanceException;
import com.nursena.payflow.wallet.domain.exception.InvalidMoneyAmountException;
import com.nursena.payflow.wallet.domain.exception.WalletNotActiveException;
import org.junit.jupiter.api.Test;

class WalletTest {

    private static final Instant NOW = Instant.parse("2026-07-12T12:00:00Z");

    @Test
    void shouldOpenWalletWithZeroBalance() {
        Wallet wallet = Wallet.open(UUID.randomUUID(), Currency.TRY, NOW);

        assertThat(wallet.balance()).isEqualTo(Money.of("0.00", Currency.TRY));
        assertThat(wallet.status()).isEqualTo(WalletStatus.ACTIVE);
        assertThat(wallet.createdAt()).isEqualTo(NOW);
    }

    @Test
    void shouldCreditPositiveAmount() {
        Wallet wallet = Wallet.open(UUID.randomUUID(), Currency.TRY, NOW);

        wallet.credit(Money.of("125.50", Currency.TRY));

        assertThat(wallet.balance()).isEqualTo(Money.of("125.50", Currency.TRY));
    }

    @Test
    void shouldDebitWhenBalanceIsSufficient() {
        Wallet wallet = Wallet.open(UUID.randomUUID(), Currency.TRY, NOW);
        wallet.credit(Money.of("100.00", Currency.TRY));

        wallet.debit(Money.of("40.25", Currency.TRY));

        assertThat(wallet.balance()).isEqualTo(Money.of("59.75", Currency.TRY));
    }

    @Test
    void shouldRejectDebitWhenBalanceIsInsufficient() {
        Wallet wallet = Wallet.open(UUID.randomUUID(), Currency.TRY, NOW);
        wallet.credit(Money.of("10.00", Currency.TRY));

        assertThatThrownBy(() -> wallet.debit(Money.of("10.01", Currency.TRY)))
                .isInstanceOf(InsufficientBalanceException.class);
    }

    @Test
    void shouldRejectNonPositiveAmount() {
        Wallet wallet = Wallet.open(UUID.randomUUID(), Currency.TRY, NOW);

        assertThatThrownBy(() -> wallet.credit(Money.of("0.00", Currency.TRY)))
                .isInstanceOf(InvalidMoneyAmountException.class);
    }

    @Test
    void shouldRejectOperationsWhenSuspended() {
        Wallet wallet = Wallet.open(UUID.randomUUID(), Currency.TRY, NOW);
        wallet.suspend();

        assertThatThrownBy(() -> wallet.credit(Money.of("1.00", Currency.TRY)))
                .isInstanceOf(WalletNotActiveException.class);
    }
}

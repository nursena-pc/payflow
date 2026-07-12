package com.nursena.payflow.wallet.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.wallet.domain.exception.InsufficientBalanceException;
import com.nursena.payflow.wallet.domain.exception.InvalidMoneyAmountException;
import com.nursena.payflow.wallet.domain.exception.WalletNotActiveException;

public final class Wallet {

    private final UUID id;
    private final UUID ownerId;
    private Money balance;
    private final Instant createdAt;
    private WalletStatus status;

    private Wallet(
            UUID id,
            UUID ownerId,
            Money balance,
            WalletStatus status,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.balance = Objects.requireNonNull(balance);
        this.status = Objects.requireNonNull(status);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static Wallet open(UUID ownerId, Currency currency, Instant now) {
        return new Wallet(
                UUID.randomUUID(),
                ownerId,
                Money.zero(currency),
                WalletStatus.ACTIVE,
                now
        );
    }

    public static Wallet rehydrate(
            UUID id,
            UUID ownerId,
            Money balance,
            WalletStatus status,
            Instant createdAt
    ) {
        return new Wallet(id, ownerId, balance, status, createdAt);
    }

    public void credit(Money amount) {
        ensureActive();
        ensurePositive(amount);
        balance = balance.add(amount);
    }

    public void debit(Money amount) {
        ensureActive();
        ensurePositive(amount);
        if (balance.isLessThan(amount)) {
            throw new InsufficientBalanceException();
        }
        balance = balance.subtract(amount);
    }

    public void suspend() {
        status = WalletStatus.SUSPENDED;
    }

    private void ensureActive() {
        if (status != WalletStatus.ACTIVE) {
            throw new WalletNotActiveException();
        }
    }

    private void ensurePositive(Money amount) {
        if (!amount.isPositive()) {
            throw new InvalidMoneyAmountException();
        }
    }

    public UUID id() {
        return id;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public Money balance() {
        return balance;
    }

    public WalletStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }
}

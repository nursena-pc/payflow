package com.nursena.payflow.ledger.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.ledger.domain.model.LedgerEntryType;
import com.nursena.payflow.wallet.domain.model.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ledger_entries")
class LedgerEntryJpaEntity {

    @Id
    private UUID id;

    @Column(
        name = "transaction_id",
        nullable = false
    )
    private UUID transactionId;

    @Column(
        name = "wallet_id",
        nullable = false
    )
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "entry_type",
        nullable = false,
        length = 10
    )
    private LedgerEntryType entryType;

    @Column(
        nullable = false,
        precision = 19,
        scale = 2
    )
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(
        nullable = false,
        length = 3
    )
    private Currency currency;

    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private Instant createdAt;

    protected LedgerEntryJpaEntity() {
    }

    LedgerEntryJpaEntity(
        UUID id,
        UUID transactionId,
        UUID walletId,
        LedgerEntryType entryType,
        BigDecimal amount,
        Currency currency,
        Instant createdAt
    ) {
        this.id = id;
        this.transactionId = transactionId;
        this.walletId = walletId;
        this.entryType = entryType;
        this.amount = amount;
        this.currency = currency;
        this.createdAt = createdAt;
    }

    UUID getId() {
        return id;
    }

    UUID getTransactionId() {
        return transactionId;
    }

    UUID getWalletId() {
        return walletId;
    }

    LedgerEntryType getEntryType() {
        return entryType;
    }

    BigDecimal getAmount() {
        return amount;
    }

    Currency getCurrency() {
        return currency;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}

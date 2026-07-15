package com.nursena.payflow.wallet.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.wallet.domain.model.Currency;
import com.nursena.payflow.wallet.domain.model.WalletStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.Objects;

@Entity
@Table(name = "wallets")
class WalletJpaEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, unique = true)
    private UUID ownerId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WalletStatus status;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WalletJpaEntity() {
    }

    WalletJpaEntity(
            UUID id,
            UUID ownerId,
            BigDecimal balance,
            Currency currency,
            WalletStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.ownerId = ownerId;
        this.balance = balance;
        this.currency = currency;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    void updateState(
        BigDecimal balance,
        WalletStatus status,
        Instant updatedAt
    ) {
        this.balance = Objects.requireNonNull(
            balance,
            "balance must not be null"
        );
        this.status = Objects.requireNonNull(
            status,
            "status must not be null"
        );
        this.updatedAt = Objects.requireNonNull(
            updatedAt,
            "updatedAt must not be null"
        );
    }

    UUID getId() {
        return id;
    }

    UUID getOwnerId() {
        return ownerId;
    }

    BigDecimal getBalance() {
        return balance;
    }

    Currency getCurrency() {
        return currency;
    }

    WalletStatus getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}

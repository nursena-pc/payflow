package com.nursena.payflow.transaction.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.transaction.domain.model.TransactionStatus;
import com.nursena.payflow.transaction.domain.model.TransactionType;
import com.nursena.payflow.wallet.domain.model.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "payment_transactions")
class PaymentTransactionJpaEntity {

    @Id
    private UUID id;

    @Column(name = "source_wallet_id")
    private UUID sourceWalletId;

    @Column(name = "target_wallet_id")
    private UUID targetWalletId;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "transaction_type",
        nullable = false,
        length = 30
    )
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(
        nullable = false,
        precision = 19,
        scale = 2
    )
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @Column(
        name = "idempotency_key",
        length = 100
    )
    private String idempotencyKey;

    @Column(
        name = "failure_reason",
        length = 500
    )
    private String failureReason;

    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected PaymentTransactionJpaEntity() {
    }

    PaymentTransactionJpaEntity(
        UUID id,
        UUID sourceWalletId,
        UUID targetWalletId,
        TransactionType transactionType,
        TransactionStatus status,
        BigDecimal amount,
        Currency currency,
        String idempotencyKey,
        String failureReason,
        Instant createdAt,
        Instant completedAt
    ) {
        this.id = id;
        this.sourceWalletId = sourceWalletId;
        this.targetWalletId = targetWalletId;
        this.transactionType = transactionType;
        this.status = status;
        this.amount = amount;
        this.currency = currency;
        this.idempotencyKey = idempotencyKey;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    void updateState(
        TransactionStatus status,
        Instant completedAt
    ) {
        this.status = Objects.requireNonNull(
            status,
            "status must not be null"
        );
        this.completedAt = completedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getSourceWalletId() {
        return sourceWalletId;
    }

    UUID getTargetWalletId() {
        return targetWalletId;
    }

    TransactionType getTransactionType() {
        return transactionType;
    }

    TransactionStatus getStatus() {
        return status;
    }

    BigDecimal getAmount() {
        return amount;
    }

    Currency getCurrency() {
        return currency;
    }

    String getIdempotencyKey() {
        return idempotencyKey;
    }

    String getFailureReason() {
        return failureReason;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getCompletedAt() {
        return completedAt;
    }
}

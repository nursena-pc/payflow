package com.nursena.payflow.transaction.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.transaction.domain.model.IdempotencyKey;
import com.nursena.payflow.transaction.domain.model.PaymentTransaction;

public interface PaymentTransactionRepositoryPort {

    Optional<PaymentTransaction>
    findBySourceWalletIdAndIdempotencyKey(
        UUID sourceWalletId,
        IdempotencyKey idempotencyKey
    );

    PaymentTransaction save(
        PaymentTransaction transaction
    );

    PaymentTransaction update(
        PaymentTransaction transaction
    );
}

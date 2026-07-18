package com.nursena.payflow.transaction.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.ledger.application.port.out.LedgerRepositoryPort;
import com.nursena.payflow.ledger.domain.model.DoubleEntryLedger;
import com.nursena.payflow.ledger.domain.model.LedgerEntryType;
import com.nursena.payflow.transaction.application.port.in.TransferMoneyCommand;
import com.nursena.payflow.transaction.application.port.in.TransferMoneyResult;
import com.nursena.payflow.transaction.application.port.out.PaymentTransactionRepositoryPort;
import com.nursena.payflow.transaction.domain.exception.SelfTransferNotAllowedException;
import com.nursena.payflow.transaction.domain.model.PaymentTransaction;
import com.nursena.payflow.transaction.domain.model.TransactionStatus;
import com.nursena.payflow.wallet.application.port.out.WalletRepositoryPort;
import com.nursena.payflow.wallet.domain.exception.InsufficientBalanceException;
import com.nursena.payflow.wallet.domain.exception.WalletNotFoundException;
import com.nursena.payflow.wallet.domain.model.Currency;
import com.nursena.payflow.wallet.domain.model.Money;
import com.nursena.payflow.wallet.domain.model.Wallet;
import com.nursena.payflow.wallet.domain.model.WalletStatus;
import com.nursena.payflow.transaction.domain.exception.IdempotencyConflictException;
import com.nursena.payflow.transaction.domain.exception.IdempotencyRequestInProgressException;
import com.nursena.payflow.transaction.domain.model.IdempotencyKey;
import com.nursena.payflow.transaction.domain.model.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.nursena.payflow.transaction.application.model.TransferCompletedEvent;
import com.nursena.payflow.transaction.application.port.out.TransferCompletedEventRecorderPort;

@ExtendWith(MockitoExtension.class)
class TransferMoneyServiceTest {

    private static final UUID OWNER_ID =
        UUID.fromString(
            "8805681d-d537-42f2-8906-5da1f0666ab7"
        );

    private static final UUID TRANSACTION_ID =
        UUID.fromString(
            "b4077781-34f4-466f-8e61-b79ca906bc98"
        );

    private static final UUID SOURCE_WALLET_ID =
        UUID.fromString(
            "11111111-1111-1111-1111-111111111111"
        );

    private static final UUID TARGET_WALLET_ID =
        UUID.fromString(
            "22222222-2222-2222-2222-222222222222"
        );

    private static final UUID TARGET_OWNER_ID =
        UUID.fromString(
            "33333333-3333-3333-3333-333333333333"
        );

    private static final Instant NANOSECOND_TIME =
        Instant.parse("2026-07-15T18:30:00.123456789Z");

    private static final Instant NOW =
        Instant.parse("2026-07-15T18:30:00Z");

    @Mock
    private WalletRepositoryPort walletRepository;

    @Mock
    private PaymentTransactionRepositoryPort
        transactionRepository;

    @Mock
    private LedgerRepositoryPort ledgerRepository;

    @Mock
    private TransferCompletedEventRecorderPort
        completedEventRecorder;

    private TransferMoneyService service;

    @BeforeEach
    void setUp() {
        service = new TransferMoneyService(
            walletRepository,
            transactionRepository,
            ledgerRepository,
            completedEventRecorder,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldTransferMoneyAndCreateBalancedLedger() {
        Wallet sourceWallet = sourceWallet("200.00");
        Wallet targetWallet = targetWallet("50.00");

        when(walletRepository.findByOwnerId(OWNER_ID))
            .thenReturn(Optional.of(sourceWallet));
        when(
            transactionRepository
                .findBySourceWalletIdAndIdempotencyKey(
                    SOURCE_WALLET_ID,
                    new IdempotencyKey("request-1")
                )
        ).thenReturn(Optional.empty());

        when(walletRepository.findById(TARGET_WALLET_ID))
            .thenReturn(Optional.of(targetWallet));

        when(transactionRepository.save(
            any(PaymentTransaction.class)
        )).thenAnswer(invocation -> {
            PaymentTransaction transaction =
                invocation.getArgument(0);

            assertThat(transaction.status())
                .isEqualTo(TransactionStatus.PENDING);

            return transaction;
        });

        when(walletRepository.update(any(Wallet.class)))
            .thenAnswer(invocation ->
                invocation.getArgument(0)
            );

        when(ledgerRepository.save(
            any(DoubleEntryLedger.class)
        )).thenAnswer(invocation ->
            invocation.getArgument(0)
        );

        when(transactionRepository.update(
            any(PaymentTransaction.class)
        )).thenAnswer(invocation ->
            invocation.getArgument(0)
        );

        TransferMoneyResult result = service.transfer(
            command("125.50", TARGET_WALLET_ID)
        );

        assertThat(sourceWallet.balance().amount())
            .isEqualByComparingTo("74.50");

        assertThat(targetWallet.balance().amount())
            .isEqualByComparingTo("175.50");

        assertThat(result.sourceWalletId())
            .isEqualTo(SOURCE_WALLET_ID);

        assertThat(result.targetWalletId())
            .isEqualTo(TARGET_WALLET_ID);

        assertThat(result.amount())
            .isEqualByComparingTo("125.50");

        assertThat(result.currency())
            .isEqualTo(Currency.TRY);

        assertThat(result.status())
            .isEqualTo(TransactionStatus.COMPLETED);

        assertThat(result.completedAt())
            .isEqualTo(NOW);

        ArgumentCaptor<DoubleEntryLedger> ledgerCaptor =
            ArgumentCaptor.forClass(
                DoubleEntryLedger.class
            );

        verify(ledgerRepository)
            .save(ledgerCaptor.capture());

        DoubleEntryLedger ledger =
            ledgerCaptor.getValue();

        assertThat(ledger.debitEntry().walletId())
            .isEqualTo(SOURCE_WALLET_ID);

        assertThat(ledger.debitEntry().type())
            .isEqualTo(LedgerEntryType.DEBIT);

        assertThat(ledger.creditEntry().walletId())
            .isEqualTo(TARGET_WALLET_ID);

        assertThat(ledger.creditEntry().type())
            .isEqualTo(LedgerEntryType.CREDIT);

        assertThat(ledger.debitEntry().amount())
            .isEqualTo(ledger.creditEntry().amount());

        verify(walletRepository).update(sourceWallet);
        verify(walletRepository).update(targetWallet);

        ArgumentCaptor<TransferCompletedEvent>
            eventCaptor =
            ArgumentCaptor.forClass(
                TransferCompletedEvent.class
            );

        verify(completedEventRecorder)
            .record(eventCaptor.capture());

        TransferCompletedEvent event =
            eventCaptor.getValue();

        assertThat(event.eventId())
            .isNotNull();

        assertThat(event.eventType())
            .isEqualTo(
                TransferCompletedEvent.TYPE
            );

        assertThat(event.eventVersion())
            .isEqualTo(
                TransferCompletedEvent.VERSION
            );

        assertThat(event.occurredAt())
            .isEqualTo(NOW);

        assertThat(event.transactionId())
            .isEqualTo(result.transactionId());

        assertThat(event.sourceWalletId())
            .isEqualTo(SOURCE_WALLET_ID);

        assertThat(event.targetWalletId())
            .isEqualTo(TARGET_WALLET_ID);

        assertThat(event.amount())
            .isEqualTo("125.50");

        assertThat(event.currency())
            .isEqualTo("TRY");
    }

    @Test
    void shouldRejectOwnerWithoutWallet() {
        when(walletRepository.findByOwnerId(OWNER_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service.transfer(
                command("25.00", TARGET_WALLET_ID)
            )
        )
            .isInstanceOf(WalletNotFoundException.class);

        verify(walletRepository, never())
            .findById(any(UUID.class));

        verifyNoWrites();
    }

    @Test
    void shouldRejectMissingTargetWallet() {
        Wallet sourceWallet = sourceWallet("200.00");

        when(walletRepository.findByOwnerId(OWNER_ID))
            .thenReturn(Optional.of(sourceWallet));

        when(walletRepository.findById(TARGET_WALLET_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service.transfer(
                command("25.00", TARGET_WALLET_ID)
            )
        )
            .isInstanceOf(WalletNotFoundException.class);

        assertThat(sourceWallet.balance().amount())
            .isEqualByComparingTo("200.00");

        verifyNoWrites();
    }

    @Test
    void shouldRejectInsufficientBalance() {
        Wallet sourceWallet = sourceWallet("10.00");
        Wallet targetWallet = targetWallet("50.00");

        when(walletRepository.findByOwnerId(OWNER_ID))
            .thenReturn(Optional.of(sourceWallet));

        when(walletRepository.findById(TARGET_WALLET_ID))
            .thenReturn(Optional.of(targetWallet));

        assertThatThrownBy(() ->
            service.transfer(
                command("25.00", TARGET_WALLET_ID)
            )
        )
            .isInstanceOf(
                InsufficientBalanceException.class
            );

        assertThat(sourceWallet.balance().amount())
            .isEqualByComparingTo("10.00");

        assertThat(targetWallet.balance().amount())
            .isEqualByComparingTo("50.00");

        verifyNoWrites();
    }

    @Test
    void shouldRejectTransferToSameWallet() {
        Wallet sourceWallet = sourceWallet("200.00");

        when(walletRepository.findByOwnerId(OWNER_ID))
            .thenReturn(Optional.of(sourceWallet));

        when(walletRepository.findById(SOURCE_WALLET_ID))
            .thenReturn(Optional.of(sourceWallet));

        assertThatThrownBy(() ->
            service.transfer(
                command("25.00", SOURCE_WALLET_ID)
            )
        )
            .isInstanceOf(
                SelfTransferNotAllowedException.class
            );

        assertThat(sourceWallet.balance().amount())
            .isEqualByComparingTo("200.00");

        verifyNoWrites();
    }

    private void verifyNoWrites() {
        verify(transactionRepository, never())
            .save(any(PaymentTransaction.class));

        verify(walletRepository, never())
            .update(any(Wallet.class));

        verify(ledgerRepository, never())
            .save(any(DoubleEntryLedger.class));

        verify(transactionRepository, never())
            .update(any(PaymentTransaction.class));
        verify(completedEventRecorder, never())
            .record(
                any(TransferCompletedEvent.class)
            );
    }

    private static TransferMoneyCommand command(
        String amount,
        UUID targetWalletId
    ) {
        return new TransferMoneyCommand(
            OWNER_ID,
            targetWalletId,
            new BigDecimal(amount),
            "request-1"
        );
    }

    private static Wallet sourceWallet(String balance) {
        return Wallet.rehydrate(
            SOURCE_WALLET_ID,
            OWNER_ID,
            Money.of(balance, Currency.TRY),
            WalletStatus.ACTIVE,
            NOW
        );
    }

    private static Wallet targetWallet(String balance) {
        return Wallet.rehydrate(
            TARGET_WALLET_ID,
            TARGET_OWNER_ID,
            Money.of(balance, Currency.TRY),
            WalletStatus.ACTIVE,
            NOW
        );
    }

    @Test
    void shouldReplayCompletedTransferWithoutNewWrites() {
        Wallet sourceWallet = sourceWallet("74.50");

        PaymentTransaction completedTransaction =
            completedTransaction(
                "125.50",
                TARGET_WALLET_ID
            );

        when(walletRepository.findByOwnerId(OWNER_ID))
            .thenReturn(Optional.of(sourceWallet));

        when(
            transactionRepository
                .findBySourceWalletIdAndIdempotencyKey(
                    SOURCE_WALLET_ID,
                    new IdempotencyKey("request-1")
                )
        ).thenReturn(
            Optional.of(completedTransaction)
        );

        TransferMoneyResult result = service.transfer(
            command("125.50", TARGET_WALLET_ID)
        );

        assertThat(result.transactionId())
            .isEqualTo(TRANSACTION_ID);

        assertThat(result.status())
            .isEqualTo(TransactionStatus.COMPLETED);

        assertThat(result.amount())
            .isEqualByComparingTo("125.50");

        verify(walletRepository, never())
            .findById(any(UUID.class));

        verifyNoWrites();
    }

    @Test
    void shouldRejectReusedKeyWithDifferentAmount() {
        Wallet sourceWallet = sourceWallet("74.50");

        PaymentTransaction completedTransaction =
            completedTransaction(
                "125.50",
                TARGET_WALLET_ID
            );

        when(walletRepository.findByOwnerId(OWNER_ID))
            .thenReturn(Optional.of(sourceWallet));

        when(
            transactionRepository
                .findBySourceWalletIdAndIdempotencyKey(
                    SOURCE_WALLET_ID,
                    new IdempotencyKey("request-1")
                )
        ).thenReturn(
            Optional.of(completedTransaction)
        );

        assertThatThrownBy(() ->
            service.transfer(
                command("100.00", TARGET_WALLET_ID)
            )
        )
            .isInstanceOf(
                IdempotencyConflictException.class
            );

        verify(walletRepository, never())
            .findById(any(UUID.class));

        verifyNoWrites();
    }

    @Test
    void shouldRejectReplayWhileTransferIsPending() {
        Wallet sourceWallet = sourceWallet("200.00");

        PaymentTransaction pendingTransaction =
            pendingTransaction(
                "125.50",
                TARGET_WALLET_ID
            );

        when(walletRepository.findByOwnerId(OWNER_ID))
            .thenReturn(Optional.of(sourceWallet));

        when(
            transactionRepository
                .findBySourceWalletIdAndIdempotencyKey(
                    SOURCE_WALLET_ID,
                    new IdempotencyKey("request-1")
                )
        ).thenReturn(
            Optional.of(pendingTransaction)
        );

        assertThatThrownBy(() ->
            service.transfer(
                command("125.50", TARGET_WALLET_ID)
            )
        )
            .isInstanceOf(
                IdempotencyRequestInProgressException.class
            );

        verify(walletRepository, never())
            .findById(any(UUID.class));

        verifyNoWrites();
    }

    @Test
    void shouldNormalizeTransactionTimestampsToMicroseconds() {
        TransferMoneyService timestampService =
            new TransferMoneyService(
                walletRepository,
                transactionRepository,
                ledgerRepository,
                completedEventRecorder,
                Clock.fixed(
                    NANOSECOND_TIME,
                    ZoneOffset.UTC
                )
            );

        Wallet sourceWallet = sourceWallet("200.00");
        Wallet targetWallet = targetWallet("50.00");

        when(walletRepository.findByOwnerId(OWNER_ID))
            .thenReturn(Optional.of(sourceWallet));

        when(
            transactionRepository
                .findBySourceWalletIdAndIdempotencyKey(
                    SOURCE_WALLET_ID,
                    new IdempotencyKey("request-1")
                )
        ).thenReturn(Optional.empty());

        when(walletRepository.findById(TARGET_WALLET_ID))
            .thenReturn(Optional.of(targetWallet));

        when(transactionRepository.save(
            any(PaymentTransaction.class)
        )).thenAnswer(invocation ->
            invocation.getArgument(0)
        );

        when(walletRepository.update(any(Wallet.class)))
            .thenAnswer(invocation ->
                invocation.getArgument(0)
            );

        when(ledgerRepository.save(
            any(DoubleEntryLedger.class)
        )).thenAnswer(invocation ->
            invocation.getArgument(0)
        );

        when(transactionRepository.update(
            any(PaymentTransaction.class)
        )).thenAnswer(invocation ->
            invocation.getArgument(0)
        );

        TransferMoneyResult result =
            timestampService.transfer(
                command("25.00", TARGET_WALLET_ID)
            );

        Instant expected =
            Instant.parse(
                "2026-07-15T18:30:00.123456Z"
            );

        assertThat(result.createdAt())
            .isEqualTo(expected);

        assertThat(result.completedAt())
            .isEqualTo(expected);
    }

    private static PaymentTransaction completedTransaction(
        String amount,
        UUID targetWalletId
    ) {
        return PaymentTransaction.rehydrate(
            TRANSACTION_ID,
            SOURCE_WALLET_ID,
            targetWalletId,
            Money.of(amount, Currency.TRY),
            new IdempotencyKey("request-1"),
            TransactionType.TRANSFER,
            TransactionStatus.COMPLETED,
            NOW.minusSeconds(1),
            NOW
        );
    }

    private static PaymentTransaction pendingTransaction(
        String amount,
        UUID targetWalletId
    ) {
        return PaymentTransaction.rehydrate(
            TRANSACTION_ID,
            SOURCE_WALLET_ID,
            targetWalletId,
            Money.of(amount, Currency.TRY),
            new IdempotencyKey("request-1"),
            TransactionType.TRANSFER,
            TransactionStatus.PENDING,
            NOW,
            null
        );
    }

}

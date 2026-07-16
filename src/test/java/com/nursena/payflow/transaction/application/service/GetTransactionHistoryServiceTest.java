package com.nursena.payflow.transaction.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.transaction.application.model.TransactionDirection;
import com.nursena.payflow.transaction.application.model.TransactionHistoryItem;
import com.nursena.payflow.transaction.application.model.TransactionHistoryPage;
import com.nursena.payflow.transaction.application.port.in.GetTransactionHistoryQuery;
import com.nursena.payflow.transaction.application.port.out.TransactionHistoryQueryPort;
import com.nursena.payflow.transaction.domain.model.TransactionStatus;
import com.nursena.payflow.transaction.domain.model.TransactionType;
import com.nursena.payflow.wallet.application.port.out.WalletRepositoryPort;
import com.nursena.payflow.wallet.domain.exception.WalletNotFoundException;
import com.nursena.payflow.wallet.domain.model.Currency;
import com.nursena.payflow.wallet.domain.model.Wallet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetTransactionHistoryServiceTest {

    private static final UUID OWNER_ID =
        UUID.fromString(
            "8805681d-d537-42f2-8906-5da1f0666ab7"
        );

    private static final UUID WALLET_ID =
        UUID.fromString(
            "11111111-1111-1111-1111-111111111111"
        );

    private static final UUID COUNTERPARTY_WALLET_ID =
        UUID.fromString(
            "22222222-2222-2222-2222-222222222222"
        );

    private static final UUID TRANSACTION_ID =
        UUID.fromString(
            "b4077781-34f4-466f-8e61-b79ca906bc98"
        );

    private static final Instant CREATED_AT =
        Instant.parse(
            "2026-07-16T10:00:00Z"
        );

    private WalletRepositoryPort walletRepository;

    private TransactionHistoryQueryPort
        transactionHistoryQueryPort;

    private GetTransactionHistoryService service;

    @BeforeEach
    void setUp() {
        walletRepository =
            mock(WalletRepositoryPort.class);

        transactionHistoryQueryPort =
            mock(TransactionHistoryQueryPort.class);

        service = new GetTransactionHistoryService(
            walletRepository,
            transactionHistoryQueryPort
        );
    }

    @Test
    void shouldReturnAuthenticatedUsersTransactionHistory() {
        Wallet wallet = mock(Wallet.class);

        when(wallet.id())
            .thenReturn(WALLET_ID);

        when(walletRepository.findByOwnerId(OWNER_ID))
            .thenReturn(Optional.of(wallet));

        TransactionHistoryPage expectedPage =
            historyPage();

        when(
            transactionHistoryQueryPort
                .findByWalletId(
                    WALLET_ID,
                    0,
                    20
                )
        ).thenReturn(expectedPage);

        TransactionHistoryPage result =
            service.getTransactionHistory(
                new GetTransactionHistoryQuery(
                    OWNER_ID,
                    0,
                    20
                )
            );

        assertThat(result)
            .isSameAs(expectedPage);

        verify(walletRepository)
            .findByOwnerId(OWNER_ID);

        verify(transactionHistoryQueryPort)
            .findByWalletId(
                WALLET_ID,
                0,
                20
            );
    }

    @Test
    void shouldFailWhenAuthenticatedUserHasNoWallet() {
        when(walletRepository.findByOwnerId(OWNER_ID))
            .thenReturn(Optional.empty());

        GetTransactionHistoryQuery query =
            new GetTransactionHistoryQuery(
                OWNER_ID,
                0,
                20
            );

        assertThatThrownBy(() ->
            service.getTransactionHistory(query)
        )
            .isInstanceOf(
                WalletNotFoundException.class
            )
            .hasMessage(
                "Wallet could not be found."
            );

        verify(walletRepository)
            .findByOwnerId(OWNER_ID);

        verifyNoInteractions(
            transactionHistoryQueryPort
        );
    }

    private static TransactionHistoryPage historyPage() {
        TransactionHistoryItem item =
            new TransactionHistoryItem(
                TRANSACTION_ID,
                TransactionType.TRANSFER,
                TransactionDirection.OUTGOING,
                COUNTERPARTY_WALLET_ID,
                new BigDecimal("125.50"),
                Currency.TRY,
                TransactionStatus.COMPLETED,
                CREATED_AT,
                CREATED_AT
            );

        return new TransactionHistoryPage(
            List.of(item),
            0,
            20,
            1,
            1
        );
    }
}

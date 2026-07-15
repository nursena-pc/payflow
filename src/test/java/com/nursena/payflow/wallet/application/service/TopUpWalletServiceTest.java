package com.nursena.payflow.wallet.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.wallet.application.port.in.TopUpWalletCommand;
import com.nursena.payflow.wallet.application.port.in.TopUpWalletResult;
import com.nursena.payflow.wallet.application.port.out.WalletRepositoryPort;
import com.nursena.payflow.wallet.domain.exception.InvalidMoneyAmountException;
import com.nursena.payflow.wallet.domain.exception.WalletNotFoundException;
import com.nursena.payflow.wallet.domain.model.Currency;
import com.nursena.payflow.wallet.domain.model.Money;
import com.nursena.payflow.wallet.domain.model.Wallet;
import com.nursena.payflow.wallet.domain.model.WalletStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TopUpWalletServiceTest {

    private static final UUID WALLET_ID =
        UUID.fromString(
            "461ffd4c-29cc-4dbf-82b5-c9af3e1da8db"
        );

    private static final UUID OWNER_ID =
        UUID.fromString(
            "8805681d-d537-42f2-8906-5da1f0666ab7"
        );

    private static final Instant CREATED_AT =
        Instant.parse("2026-07-15T12:00:00Z");

    @Mock
    private WalletRepositoryPort walletRepository;

    private TopUpWalletService service;

    @BeforeEach
    void setUp() {
        service = new TopUpWalletService(
            walletRepository
        );
    }

    @Test
    void shouldCreditCurrentWallet() {
        Wallet wallet = activeWallet();

        when(walletRepository.findByOwnerId(OWNER_ID))
            .thenReturn(Optional.of(wallet));

        when(walletRepository.update(any(Wallet.class)))
            .thenAnswer(invocation ->
                invocation.getArgument(0)
            );

        TopUpWalletResult result = service.topUp(
            new TopUpWalletCommand(
                OWNER_ID,
                new BigDecimal("250.00")
            )
        );

        assertThat(result.id())
            .isEqualTo(WALLET_ID);

        assertThat(result.balance())
            .isEqualByComparingTo(
                new BigDecimal("350.00")
            );

        assertThat(result.currency())
            .isEqualTo(Currency.TRY);

        assertThat(result.status())
            .isEqualTo(WalletStatus.ACTIVE);

        verify(walletRepository)
            .findByOwnerId(OWNER_ID);

        verify(walletRepository)
            .update(wallet);
    }

    @Test
    void shouldRejectOwnerWithoutWallet() {
        when(walletRepository.findByOwnerId(OWNER_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service.topUp(
                new TopUpWalletCommand(
                    OWNER_ID,
                    new BigDecimal("25.00")
                )
            )
        )
            .isInstanceOf(
                WalletNotFoundException.class
            );

        verify(walletRepository)
            .findByOwnerId(OWNER_ID);

        verify(walletRepository, never())
            .update(any(Wallet.class));
    }

    @Test
    void shouldRejectNonPositiveAmount() {
        Wallet wallet = activeWallet();

        when(walletRepository.findByOwnerId(OWNER_ID))
            .thenReturn(Optional.of(wallet));

        assertThatThrownBy(() ->
            service.topUp(
                new TopUpWalletCommand(
                    OWNER_ID,
                    new BigDecimal("0.00")
                )
            )
        )
            .isInstanceOf(
                InvalidMoneyAmountException.class
            );

        verify(walletRepository, never())
            .update(any(Wallet.class));
    }

    private static Wallet activeWallet() {
        return Wallet.rehydrate(
            WALLET_ID,
            OWNER_ID,
            Money.of("100.00", Currency.TRY),
            WalletStatus.ACTIVE,
            CREATED_AT
        );
    }
}

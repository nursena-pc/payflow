package com.nursena.payflow.wallet.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.wallet.application.port.in.GetCurrentWalletResult;
import com.nursena.payflow.wallet.application.port.out.WalletRepositoryPort;
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
class GetCurrentWalletServiceTest {

    private static final UUID WALLET_ID =
        UUID.fromString(
            "461ffd4c-29cc-4dbf-82b5-c9af3e1da8db"
        );

    private static final UUID OWNER_ID =
        UUID.fromString(
            "8805681d-d537-42f2-8906-5da1f0666ab7"
        );

    private static final Instant CREATED_AT =
        Instant.parse("2026-07-14T12:00:00Z");

    @Mock
    private WalletRepositoryPort walletRepository;

    private GetCurrentWalletService service;

    @BeforeEach
    void setUp() {
        service = new GetCurrentWalletService(
            walletRepository
        );
    }

    @Test
    void shouldReturnCurrentWallet() {
        Wallet wallet = activeWallet();

        when(walletRepository.findByOwnerId(OWNER_ID))
            .thenReturn(Optional.of(wallet));

        GetCurrentWalletResult result =
            service.getCurrentWallet(OWNER_ID);

        assertThat(result.id())
            .isEqualTo(WALLET_ID);

        assertThat(result.balance())
            .isEqualByComparingTo(
                new BigDecimal("125.50")
            );

        assertThat(result.currency())
            .isEqualTo(Currency.TRY);

        assertThat(result.status())
            .isEqualTo(WalletStatus.ACTIVE);

        assertThat(result.createdAt())
            .isEqualTo(CREATED_AT);

        verify(walletRepository)
            .findByOwnerId(OWNER_ID);
    }

    @Test
    void shouldRejectOwnerWithoutWallet() {
        when(walletRepository.findByOwnerId(OWNER_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service.getCurrentWallet(OWNER_ID)
        )
            .isInstanceOf(
                WalletNotFoundException.class
            )
            .hasMessage(
                "Wallet could not be found."
            );

        verify(walletRepository)
            .findByOwnerId(OWNER_ID);
    }

    private static Wallet activeWallet() {
        return Wallet.rehydrate(
            WALLET_ID,
            OWNER_ID,
            new Money(
                new BigDecimal("125.50"),
                Currency.TRY
            ),
            WalletStatus.ACTIVE,
            CREATED_AT
        );
    }
}

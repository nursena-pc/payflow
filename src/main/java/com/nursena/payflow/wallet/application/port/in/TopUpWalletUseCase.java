package com.nursena.payflow.wallet.application.port.in;

public interface TopUpWalletUseCase {

    TopUpWalletResult topUp(
        TopUpWalletCommand command
    );
}

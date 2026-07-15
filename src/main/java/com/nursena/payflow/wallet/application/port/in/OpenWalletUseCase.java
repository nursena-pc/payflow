package com.nursena.payflow.wallet.application.port.in;

public interface OpenWalletUseCase {

    OpenWalletResult open(OpenWalletCommand command);
}

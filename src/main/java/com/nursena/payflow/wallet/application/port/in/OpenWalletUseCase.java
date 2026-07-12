package com.nursena.payflow.wallet.application.port.in;

import java.util.UUID;

public interface OpenWalletUseCase {
    UUID open(OpenWalletCommand command);
}

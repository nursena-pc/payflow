package com.nursena.payflow.wallet.application.port.in;

import java.util.UUID;

public interface GetCurrentWalletUseCase {

    GetCurrentWalletResult getCurrentWallet(UUID ownerId);
}

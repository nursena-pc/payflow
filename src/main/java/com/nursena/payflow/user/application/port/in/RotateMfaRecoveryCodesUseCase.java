package com.nursena.payflow.user.application.port.in;

public interface RotateMfaRecoveryCodesUseCase {

    RotateMfaRecoveryCodesResult rotate(
        RotateMfaRecoveryCodesCommand command
    );
}
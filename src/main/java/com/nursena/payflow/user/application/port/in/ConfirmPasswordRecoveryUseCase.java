package com.nursena.payflow.user.application.port.in;

public interface ConfirmPasswordRecoveryUseCase {

    void confirm(
        ConfirmPasswordRecoveryCommand command
    );
}

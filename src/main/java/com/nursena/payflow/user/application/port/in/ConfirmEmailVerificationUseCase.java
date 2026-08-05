package com.nursena.payflow.user.application.port.in;

public interface ConfirmEmailVerificationUseCase {

    void confirm(
        ConfirmEmailVerificationCommand command
    );
}

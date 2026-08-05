package com.nursena.payflow.user.application.port.in;

public interface RequestPasswordRecoveryUseCase {

    void request(
        RequestPasswordRecoveryCommand command
    );
}

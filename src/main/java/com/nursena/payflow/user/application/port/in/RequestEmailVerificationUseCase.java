package com.nursena.payflow.user.application.port.in;

public interface RequestEmailVerificationUseCase {

    void request(
        RequestEmailVerificationCommand command
    );
}

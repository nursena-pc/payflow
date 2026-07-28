package com.nursena.payflow.user.application.port.in;

public interface RotateRefreshCredentialsUseCase {

    RotateRefreshCredentialsResult rotate(
        RotateRefreshCredentialsCommand command
    );
}

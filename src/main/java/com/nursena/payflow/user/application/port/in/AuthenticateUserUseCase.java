package com.nursena.payflow.user.application.port.in;

public interface AuthenticateUserUseCase {

    AuthenticateUserResult authenticate(
        AuthenticateUserCommand command
    );
}

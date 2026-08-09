package com.nursena.payflow.user.application.port.in;

public interface ConfirmMfaLoginChallengeUseCase {

    AuthenticatedUserResult confirm(
        ConfirmMfaLoginChallengeCommand command
    );
}

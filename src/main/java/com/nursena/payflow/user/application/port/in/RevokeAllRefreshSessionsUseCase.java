package com.nursena.payflow.user.application.port.in;

public interface RevokeAllRefreshSessionsUseCase {

    void revoke(
        RevokeAllRefreshSessionsCommand command
    );
}

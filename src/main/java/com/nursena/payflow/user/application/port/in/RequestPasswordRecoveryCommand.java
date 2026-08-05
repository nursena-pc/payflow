package com.nursena.payflow.user.application.port.in;

import java.util.Objects;

public record RequestPasswordRecoveryCommand(
    String email
) {

    public RequestPasswordRecoveryCommand {
        Objects.requireNonNull(
            email,
            "email must not be null"
        );
    }

    @Override
    public String toString() {
        return "RequestPasswordRecoveryCommand[redacted]";
    }
}

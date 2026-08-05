package com.nursena.payflow.user.application.port.in;

import java.util.Objects;

public record RequestEmailVerificationCommand(
    String email
) {

    public RequestEmailVerificationCommand {
        Objects.requireNonNull(
            email,
            "email must not be null"
        );
    }

    @Override
    public String toString() {
        return "RequestEmailVerificationCommand[redacted]";
    }
}

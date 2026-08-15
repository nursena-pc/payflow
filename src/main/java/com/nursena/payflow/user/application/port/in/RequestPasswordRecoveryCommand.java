package com.nursena.payflow.user.application.port.in;

import java.util.Objects;

import com.nursena.payflow.clientcontext.domain.IpAddress;

public record RequestPasswordRecoveryCommand(
    String email,
    IpAddress effectiveClientAddress
) {

    public RequestPasswordRecoveryCommand {
        Objects.requireNonNull(
            email,
            "email must not be null"
        );
        Objects.requireNonNull(
            effectiveClientAddress,
            "effectiveClientAddress must not be null"
        );
    }

    @Override
    public String toString() {
        return "RequestPasswordRecoveryCommand[redacted]";
    }
}
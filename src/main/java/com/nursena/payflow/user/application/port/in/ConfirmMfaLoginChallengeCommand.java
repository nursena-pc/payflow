package com.nursena.payflow.user.application.port.in;

import java.util.Objects;

import com.nursena.payflow.clientcontext.domain.IpAddress;

public record ConfirmMfaLoginChallengeCommand(
    String challengeToken,
    String code,
    IpAddress effectiveClientAddress
) {
    public ConfirmMfaLoginChallengeCommand {
        Objects.requireNonNull(
            effectiveClientAddress,
            "effectiveClientAddress must not be null"
        );
    }

    @Override
    public String toString() {
        return "ConfirmMfaLoginChallengeCommand[redacted]";
    }
}

package com.nursena.payflow.user.application.port.in;

import java.util.Objects;
import java.util.UUID;

public record RevokeAllRefreshSessionsCommand(
    UUID userId
) {

    public RevokeAllRefreshSessionsCommand {
        Objects.requireNonNull(
            userId,
            "userId must not be null"
        );
    }

    @Override
    public String toString() {
        return "RevokeAllRefreshSessionsCommand"
            + "[redacted]";
    }
}

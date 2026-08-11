package com.nursena.payflow.user.application.port.in;

import java.util.Objects;
import java.util.UUID;

public final class DisableMfaCommand {

    private final UUID userId;
    private final String stepUpGrant;

    public DisableMfaCommand(UUID userId, String stepUpGrant) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.stepUpGrant = stepUpGrant;
    }

    public UUID userId() {
        return userId;
    }

    public String stepUpGrant() {
        return stepUpGrant;
    }

    @Override
    public String toString() {
        return "DisableMfaCommand[userId=" + userId + ", stepUpGrant=[REDACTED]]";
    }
}

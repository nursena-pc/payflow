package com.nursena.payflow.user.application.port.in;

public record ConfirmMfaLoginChallengeCommand(
    String challengeToken,
    String code
) {
    @Override
    public String toString() {
        return "ConfirmMfaLoginChallengeCommand[redacted]";
    }
}

package com.nursena.payflow.user.application.port.in;

import java.time.Instant;
import java.util.Objects;

public record IssueStepUpGrantResult(
    String grantToken,
    String purpose,
    Instant expiresAt
) {
    public IssueStepUpGrantResult {
        Objects.requireNonNull(grantToken, "grantToken must not be null");
        Objects.requireNonNull(purpose, "purpose must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    @Override
    public String toString() {
        return "IssueStepUpGrantResult[purpose="
            + purpose
            + ", expiresAt="
            + expiresAt
            + ", grantToken=redacted]";
    }
}

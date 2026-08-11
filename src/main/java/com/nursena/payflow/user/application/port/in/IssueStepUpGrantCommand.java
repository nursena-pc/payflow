package com.nursena.payflow.user.application.port.in;

import java.util.Objects;
import java.util.UUID;

public record IssueStepUpGrantCommand(
    UUID subjectId,
    String purpose,
    String code
) {
    public IssueStepUpGrantCommand {
        Objects.requireNonNull(subjectId, "subjectId must not be null");
    }

    @Override
    public String toString() {
        return "IssueStepUpGrantCommand[subjectId="
            + subjectId
            + ", purpose="
            + purpose
            + ", code=redacted]";
    }
}

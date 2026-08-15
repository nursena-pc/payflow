package com.nursena.payflow.user.application.port.in;

import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.clientcontext.domain.IpAddress;

public record IssueStepUpGrantCommand(
    UUID subjectId,
    String purpose,
    String code,
    IpAddress effectiveClientAddress
) {
    public IssueStepUpGrantCommand {
        Objects.requireNonNull(
            subjectId,
            "subjectId must not be null"
        );
        Objects.requireNonNull(
            effectiveClientAddress,
            "effectiveClientAddress must not be null"
        );
    }

    @Override
    public String toString() {
        return "IssueStepUpGrantCommand[redacted]";
    }
}

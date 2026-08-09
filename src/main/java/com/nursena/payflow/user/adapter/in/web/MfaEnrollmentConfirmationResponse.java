package com.nursena.payflow.user.adapter.in.web;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.nursena.payflow.user.application.port.in.ConfirmMfaEnrollmentResult;
import com.nursena.payflow.user.domain.model.MfaLifecycleState;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "MfaEnrollmentConfirmationResponse")
public record MfaEnrollmentConfirmationResponse(
    MfaLifecycleState state,
    Instant activatedAt,
    @Schema(
        description = "One-time plaintext recovery codes. Store them securely; "
            + "the API does not expose this set again."
    )
    List<String> recoveryCodes
) {
    public MfaEnrollmentConfirmationResponse {
        recoveryCodes = List.copyOf(Objects.requireNonNull(
            recoveryCodes,
            "recoveryCodes must not be null"
        ));

        if (recoveryCodes.isEmpty()) {
            throw new IllegalArgumentException(
                "recoveryCodes must not be empty"
            );
        }
    }

    static MfaEnrollmentConfirmationResponse from(
        ConfirmMfaEnrollmentResult result
    ) {
        return new MfaEnrollmentConfirmationResponse(
            result.state(),
            result.activatedAt(),
            result.recoveryCodes()
        );
    }

    @Override
    public String toString() {
        return "MfaEnrollmentConfirmationResponse[redacted]";
    }
}

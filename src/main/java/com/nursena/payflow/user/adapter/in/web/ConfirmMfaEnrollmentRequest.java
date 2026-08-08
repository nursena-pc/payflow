package com.nursena.payflow.user.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConfirmMfaEnrollmentRequest(
    @NotBlank
    @Pattern(regexp = "[0-9]{6}")
    String code
) {

    @Override
    public String toString() {
        return "ConfirmMfaEnrollmentRequest[redacted]";
    }
}

package com.nursena.payflow.user.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BeginMfaEnrollmentRequest(
    @NotBlank
    @Size(max = 256)
    String currentPassword
) {

    @Override
    public String toString() {
        return "BeginMfaEnrollmentRequest[redacted]";
    }
}

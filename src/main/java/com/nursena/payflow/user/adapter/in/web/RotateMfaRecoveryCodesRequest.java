package com.nursena.payflow.user.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

public record RotateMfaRecoveryCodesRequest(
    @NotBlank String stepUpGrant
) {

    @Override
    public String toString() {
        return "RotateMfaRecoveryCodesRequest[stepUpGrant=<redacted>]";
    }
}
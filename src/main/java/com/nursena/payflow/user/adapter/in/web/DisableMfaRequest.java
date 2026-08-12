package com.nursena.payflow.user.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

public record DisableMfaRequest(
    @NotBlank String stepUpGrant
) {

    @Override
    public String toString() {
        return "DisableMfaRequest[stepUpGrant=<redacted>]";
    }
}
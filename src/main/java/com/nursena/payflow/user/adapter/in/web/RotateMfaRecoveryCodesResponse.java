package com.nursena.payflow.user.adapter.in.web;

import java.util.List;

import com.nursena.payflow.user.application.port.in.RotateMfaRecoveryCodesResult;

public record RotateMfaRecoveryCodesResponse(
    List<String> recoveryCodes
) {

    public RotateMfaRecoveryCodesResponse {
        recoveryCodes = List.copyOf(recoveryCodes);
    }

    static RotateMfaRecoveryCodesResponse from(
        RotateMfaRecoveryCodesResult result
    ) {
        return new RotateMfaRecoveryCodesResponse(
            result.recoveryCodes()
        );
    }

    @Override
    public String toString() {
        return "RotateMfaRecoveryCodesResponse[recoveryCodes=<redacted>]";
    }
}
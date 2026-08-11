package com.nursena.payflow.user.application.port.in;

import java.util.List;
import java.util.Objects;

public final class RotateMfaRecoveryCodesResult {

    private final List<String> recoveryCodes;

    public RotateMfaRecoveryCodesResult(
        List<String> recoveryCodes
    ) {
        this.recoveryCodes = List.copyOf(
            Objects.requireNonNull(
                recoveryCodes,
                "recoveryCodes must not be null"
            )
        );
    }

    public List<String> recoveryCodes() {
        return recoveryCodes;
    }
}
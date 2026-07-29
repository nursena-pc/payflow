package com.nursena.payflow.user.application.service;

import java.util.Objects;

import com.nursena.payflow.user.application.port.in.RotateRefreshCredentialsResult;

sealed interface RotateRefreshCredentialsOutcome
    permits RotateRefreshCredentialsOutcome.Succeeded,
        RotateRefreshCredentialsOutcome.Rejected {

    record Succeeded(
        RotateRefreshCredentialsResult result
    ) implements RotateRefreshCredentialsOutcome {

        public Succeeded {
            Objects.requireNonNull(
                result,
                "result must not be null"
            );
        }
    }

    enum Rejected
        implements RotateRefreshCredentialsOutcome {

        INSTANCE
    }
}

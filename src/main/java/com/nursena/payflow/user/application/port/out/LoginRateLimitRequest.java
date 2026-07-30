package com.nursena.payflow.user.application.port.out;

import java.util.Objects;

import com.nursena.payflow.user.domain.model.EmailAddress;

public record LoginRateLimitRequest(
    EmailAddress identity,
    String clientAddress
) {

    public LoginRateLimitRequest {
        Objects.requireNonNull(
            identity,
            "identity must not be null"
        );

        Objects.requireNonNull(
            clientAddress,
            "clientAddress must not be null"
        );

        if (clientAddress.isBlank()) {
            throw new IllegalArgumentException(
                "clientAddress must not be blank"
            );
        }
    }
}

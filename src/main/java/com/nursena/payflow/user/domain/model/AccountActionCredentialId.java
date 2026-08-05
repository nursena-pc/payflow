package com.nursena.payflow.user.domain.model;

import java.util.Objects;
import java.util.UUID;

public record AccountActionCredentialId(
    UUID value
) {

    public AccountActionCredentialId {
        Objects.requireNonNull(
            value,
            "value must not be null"
        );
    }

    public static AccountActionCredentialId of(
        UUID value
    ) {
        return new AccountActionCredentialId(value);
    }
}

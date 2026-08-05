package com.nursena.payflow.user.domain.model;

import java.util.Arrays;
import java.util.Objects;

public final class AccountActionCredentialDigest {

    public static final int SHA_256_LENGTH_BYTES = 32;

    private final byte[] value;

    private AccountActionCredentialDigest(
        byte[] value
    ) {
        Objects.requireNonNull(
            value,
            "value must not be null"
        );

        if (value.length != SHA_256_LENGTH_BYTES) {
            throw new IllegalArgumentException(
                "value must contain exactly "
                    + SHA_256_LENGTH_BYTES
                    + " bytes"
            );
        }

        this.value = Arrays.copyOf(
            value,
            value.length
        );
    }

    public static AccountActionCredentialDigest of(
        byte[] value
    ) {
        return new AccountActionCredentialDigest(value);
    }

    public byte[] value() {
        return Arrays.copyOf(
            value,
            value.length
        );
    }

    @Override
    public boolean equals(
        Object candidate
    ) {
        if (this == candidate) {
            return true;
        }

        if (
            candidate == null
                || getClass() != candidate.getClass()
        ) {
            return false;
        }

        AccountActionCredentialDigest other =
            (AccountActionCredentialDigest) candidate;

        return Arrays.equals(
            value,
            other.value
        );
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }

    @Override
    public String toString() {
        return "AccountActionCredentialDigest[redacted]";
    }
}

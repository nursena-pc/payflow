package com.nursena.payflow.user.domain.model;

import java.util.Arrays;

public final class MfaLoginChallengeDigest {

    private static final int SHA_256_BYTES = 32;
    private final byte[] value;

    private MfaLoginChallengeDigest(byte[] value) {
        this.value = Arrays.copyOf(value, value.length);
    }

    public static MfaLoginChallengeDigest of(byte[] value) {
        if (value == null || value.length != SHA_256_BYTES) {
            throw new IllegalArgumentException(
                "MFA login challenge digest must contain exactly 32 bytes"
            );
        }
        return new MfaLoginChallengeDigest(value);
    }

    public byte[] value() {
        return Arrays.copyOf(value, value.length);
    }

    @Override
    public boolean equals(Object candidate) {
        if (this == candidate) {
            return true;
        }
        if (!(candidate instanceof MfaLoginChallengeDigest other)) {
            return false;
        }
        return Arrays.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }

    @Override
    public String toString() {
        return "MfaLoginChallengeDigest[redacted]";
    }
}

package com.nursena.payflow.user.adapter.out.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.nursena.payflow.user.application.port.out.MfaRecoveryCodeDigestPort;
import com.nursena.payflow.user.domain.model.MfaRecoveryCodeDigest;

final class Sha256MfaRecoveryCodeDigestAdapter
    implements MfaRecoveryCodeDigestPort {

    @Override
    public MfaRecoveryCodeDigest digest(String value) {
        if (
            value == null
                || !value.matches("[A-Za-z0-9_-]{22}")
        ) {
            throw new IllegalArgumentException(
                "recovery code must be canonical Base64URL text"
            );
        }

        try {
            return MfaRecoveryCodeDigest.of(
                MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII))
            );
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                "SHA-256 is unavailable.",
                exception
            );
        }
    }
}

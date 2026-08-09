package com.nursena.payflow.user.adapter.out.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.nursena.payflow.user.application.port.out.MfaLoginChallengeDigestPort;
import com.nursena.payflow.user.domain.model.MfaLoginChallengeDigest;

final class Sha256MfaLoginChallengeDigestAdapter
    implements MfaLoginChallengeDigestPort {

    @Override
    public MfaLoginChallengeDigest digest(String value) {
        if (value == null || value.isBlank() || value.length() > 256) {
            throw new IllegalArgumentException(
                "challenge value must contain between 1 and 256 characters"
            );
        }
        try {
            return MfaLoginChallengeDigest.of(
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

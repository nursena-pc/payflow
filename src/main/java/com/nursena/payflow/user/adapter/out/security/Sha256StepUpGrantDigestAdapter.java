package com.nursena.payflow.user.adapter.out.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.nursena.payflow.user.application.port.out.StepUpGrantDigestPort;
import com.nursena.payflow.user.domain.model.StepUpGrantDigest;

final class Sha256StepUpGrantDigestAdapter
    implements StepUpGrantDigestPort {

    @Override
    public StepUpGrantDigest digest(String value) {
        if (value == null || value.isBlank() || value.length() > 256) {
            throw new IllegalArgumentException(
                "step-up grant must contain between 1 and 256 characters"
            );
        }
        try {
            return StepUpGrantDigest.of(
                MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII))
            );
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}

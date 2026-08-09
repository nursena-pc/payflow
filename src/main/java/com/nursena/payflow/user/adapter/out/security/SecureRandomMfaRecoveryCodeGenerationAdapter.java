package com.nursena.payflow.user.adapter.out.security;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import com.nursena.payflow.user.application.port.out.GeneratedMfaRecoveryCode;
import com.nursena.payflow.user.application.port.out.MfaRecoveryCodeGenerationPort;

final class SecureRandomMfaRecoveryCodeGenerationAdapter
    implements MfaRecoveryCodeGenerationPort {

    private static final int RANDOM_BYTES = 16;
    private final SecureRandom secureRandom;

    SecureRandomMfaRecoveryCodeGenerationAdapter(
        SecureRandom secureRandom
    ) {
        this.secureRandom = secureRandom;
    }

    @Override
    public GeneratedMfaRecoveryCode generate() {
        byte[] random = new byte[RANDOM_BYTES];
        secureRandom.nextBytes(random);

        try {
            return new GeneratedMfaRecoveryCode(
                Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(random)
            );
        }
        finally {
            Arrays.fill(random, (byte) 0);
        }
    }
}

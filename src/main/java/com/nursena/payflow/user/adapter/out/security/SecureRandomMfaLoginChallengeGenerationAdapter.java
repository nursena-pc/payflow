package com.nursena.payflow.user.adapter.out.security;

import java.security.SecureRandom;
import java.util.Base64;

import com.nursena.payflow.user.application.port.out.GeneratedMfaLoginChallenge;
import com.nursena.payflow.user.application.port.out.MfaLoginChallengeGenerationPort;

final class SecureRandomMfaLoginChallengeGenerationAdapter
    implements MfaLoginChallengeGenerationPort {

    private static final int RANDOM_BYTES = 32;
    private final SecureRandom secureRandom;

    SecureRandomMfaLoginChallengeGenerationAdapter(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    @Override
    public GeneratedMfaLoginChallenge generate() {
        byte[] random = new byte[RANDOM_BYTES];
        secureRandom.nextBytes(random);
        try {
            return new GeneratedMfaLoginChallenge(
                Base64.getUrlEncoder().withoutPadding().encodeToString(random)
            );
        }
        finally {
            java.util.Arrays.fill(random, (byte) 0);
        }
    }
}

package com.nursena.payflow.user.adapter.out.security;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import com.nursena.payflow.user.application.port.out.GeneratedStepUpGrant;
import com.nursena.payflow.user.application.port.out.StepUpGrantGenerationPort;

final class SecureRandomStepUpGrantGenerationAdapter
    implements StepUpGrantGenerationPort {

    private static final int RANDOM_BYTES = 32;
    private final SecureRandom secureRandom;

    SecureRandomStepUpGrantGenerationAdapter(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    @Override
    public GeneratedStepUpGrant generate() {
        byte[] random = new byte[RANDOM_BYTES];
        secureRandom.nextBytes(random);
        try {
            return new GeneratedStepUpGrant(
                Base64.getUrlEncoder().withoutPadding().encodeToString(random)
            );
        }
        finally {
            Arrays.fill(random, (byte) 0);
        }
    }
}

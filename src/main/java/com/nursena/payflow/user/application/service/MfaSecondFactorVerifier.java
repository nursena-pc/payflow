package com.nursena.payflow.user.application.service;

import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.user.domain.model.MfaAuthenticator;

interface MfaSecondFactorVerifier {

    boolean verifyAndConsume(
        UUID userId,
        MfaAuthenticator authenticator,
        String proof,
        Instant now
    );
}

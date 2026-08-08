package com.nursena.payflow.user.application.port.out;

import java.util.UUID;

import com.nursena.payflow.user.domain.model.ProtectedMfaSecret;

public interface MfaSecretProtectionPort {

    ProtectedMfaSecret protect(
        UUID userId,
        byte[] plaintextSecret
    );

    byte[] reveal(
        UUID userId,
        ProtectedMfaSecret protectedSecret
    );
}

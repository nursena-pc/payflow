package com.nursena.payflow.user.application.service;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.user.application.exception.MfaSecurityUnavailableException;
import com.nursena.payflow.user.application.port.out.MfaRecoveryCodeDigestPort;
import com.nursena.payflow.user.application.port.out.MfaRecoveryCodeRepositoryPort;
import com.nursena.payflow.user.application.port.out.MfaSecretProtectionFailureException;
import com.nursena.payflow.user.application.port.out.MfaSecretProtectionPort;
import com.nursena.payflow.user.application.port.out.TotpVerificationPort;
import com.nursena.payflow.user.domain.model.MfaAuthenticator;
import com.nursena.payflow.user.domain.model.MfaRecoveryCode;
import com.nursena.payflow.user.domain.model.MfaRecoveryCodeDigest;
import org.springframework.stereotype.Component;

@Component
class MfaLoginSecondFactorVerifier {

    private static final String TOTP_PATTERN = "[0-9]{6}";
    private static final String RECOVERY_CODE_PATTERN =
        "[A-Za-z0-9_-]{22}";

    private final MfaSecretProtectionPort secretProtection;
    private final TotpVerificationPort totpVerification;
    private final MfaRecoveryCodeDigestPort recoveryCodeDigest;
    private final MfaRecoveryCodeRepositoryPort recoveryCodeRepository;

    MfaLoginSecondFactorVerifier(
        MfaSecretProtectionPort secretProtection,
        TotpVerificationPort totpVerification,
        MfaRecoveryCodeDigestPort recoveryCodeDigest,
        MfaRecoveryCodeRepositoryPort recoveryCodeRepository
    ) {
        this.secretProtection = secretProtection;
        this.totpVerification = totpVerification;
        this.recoveryCodeDigest = recoveryCodeDigest;
        this.recoveryCodeRepository = recoveryCodeRepository;
    }

    boolean verifyAndConsume(
        UUID userId,
        MfaAuthenticator authenticator,
        String proof,
        Instant now
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(
            authenticator,
            "authenticator must not be null"
        );
        Objects.requireNonNull(now, "now must not be null");

        if (proof == null) {
            return false;
        }

        if (proof.matches(TOTP_PATTERN)) {
            return verifyTotp(userId, authenticator, proof, now);
        }

        if (proof.matches(RECOVERY_CODE_PATTERN)) {
            return verifyRecoveryCode(userId, proof, now);
        }

        return false;
    }

    private boolean verifyTotp(
        UUID userId,
        MfaAuthenticator authenticator,
        String proof,
        Instant now
    ) {
        byte[] secret;

        try {
            secret = secretProtection.reveal(
                userId,
                authenticator.protectedSecret()
            );
        }
        catch (MfaSecretProtectionFailureException exception) {
            throw new MfaSecurityUnavailableException();
        }

        try {
            return totpVerification.verify(secret, proof, now);
        }
        finally {
            Arrays.fill(secret, (byte) 0);
        }
    }

    private boolean verifyRecoveryCode(
        UUID userId,
        String proof,
        Instant now
    ) {
        MfaRecoveryCodeDigest digest;

        try {
            digest = recoveryCodeDigest.digest(proof);
        }
        catch (IllegalArgumentException exception) {
            return false;
        }

        MfaRecoveryCode recoveryCode = recoveryCodeRepository
            .findByUserIdAndDigestForUpdate(userId, digest)
            .filter(value -> value.isUsableAt(now))
            .orElse(null);

        if (recoveryCode == null) {
            return false;
        }

        recoveryCodeRepository.save(recoveryCode.consume(now));
        return true;
    }
}

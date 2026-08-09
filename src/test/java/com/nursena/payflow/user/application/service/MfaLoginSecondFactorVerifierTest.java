package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.user.application.exception.MfaSecurityUnavailableException;
import com.nursena.payflow.user.application.port.out.MfaRecoveryCodeDigestPort;
import com.nursena.payflow.user.application.port.out.MfaRecoveryCodeRepositoryPort;
import com.nursena.payflow.user.application.port.out.MfaSecretProtectionFailureException;
import com.nursena.payflow.user.application.port.out.MfaSecretProtectionPort;
import com.nursena.payflow.user.application.port.out.TotpVerificationPort;
import com.nursena.payflow.user.domain.model.MfaAuthenticator;
import com.nursena.payflow.user.domain.model.MfaLifecycleState;
import com.nursena.payflow.user.domain.model.MfaRecoveryCode;
import com.nursena.payflow.user.domain.model.MfaRecoveryCodeDigest;
import com.nursena.payflow.user.domain.model.ProtectedMfaSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MfaLoginSecondFactorVerifierTest {

    private static final UUID USER_ID =
        UUID.fromString("20e683e1-6286-4a25-95f6-0d2cab4667fd");
    private static final Instant NOW =
        Instant.parse("2026-08-09T13:00:00Z");
    private static final String RECOVERY_CODE =
        "AbCdEfGhIjKlMnOpQrStUv";

    @Mock MfaSecretProtectionPort secretProtection;
    @Mock TotpVerificationPort totpVerification;
    @Mock MfaRecoveryCodeDigestPort recoveryCodeDigest;
    @Mock MfaRecoveryCodeRepositoryPort recoveryCodeRepository;

    private MfaLoginSecondFactorVerifier verifier;
    private MfaAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        verifier = new MfaLoginSecondFactorVerifier(
            secretProtection,
            totpVerification,
            recoveryCodeDigest,
            recoveryCodeRepository
        );
        authenticator = MfaAuthenticator.rehydrate(
            USER_ID,
            MfaLifecycleState.ENABLED,
            ProtectedMfaSecret.of(new byte[49]),
            null,
            NOW.minusSeconds(120),
            NOW.minusSeconds(180),
            NOW.minusSeconds(120)
        );
    }

    @Test
    void shouldVerifyTotpAndClearRevealedSecret() {
        byte[] secret = new byte[20];
        secret[0] = 9;
        when(secretProtection.reveal(
            USER_ID,
            authenticator.protectedSecret()
        )).thenReturn(secret);
        when(totpVerification.verify(secret, "123456", NOW))
            .thenReturn(true);

        assertThat(verifier.verifyAndConsume(
            USER_ID,
            authenticator,
            "123456",
            NOW
        )).isTrue();
        assertThat(secret).containsOnly((byte) 0);
        verifyNoInteractions(recoveryCodeRepository);
    }

    @Test
    void shouldConsumeMatchingUnusedRecoveryCodeWithoutRevealingTotpSecret() {
        MfaRecoveryCodeDigest digest =
            MfaRecoveryCodeDigest.of(new byte[32]);
        MfaRecoveryCode recoveryCode = MfaRecoveryCode.issue(
            UUID.randomUUID(),
            USER_ID,
            digest,
            NOW.minusSeconds(60)
        );
        when(recoveryCodeDigest.digest(RECOVERY_CODE))
            .thenReturn(digest);
        when(recoveryCodeRepository.findByUserIdAndDigestForUpdate(
            USER_ID,
            digest
        )).thenReturn(Optional.of(recoveryCode));
        when(recoveryCodeRepository.save(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(verifier.verifyAndConsume(
            USER_ID,
            authenticator,
            RECOVERY_CODE,
            NOW
        )).isTrue();

        verify(recoveryCodeRepository).save(
            org.mockito.ArgumentMatchers.argThat(value ->
                value.consumedAt().equals(NOW)
            )
        );
        verifyNoInteractions(secretProtection, totpVerification);
    }

    @Test
    void shouldRejectConsumedRecoveryCodeWithoutMutatingItAgain() {
        MfaRecoveryCodeDigest digest =
            MfaRecoveryCodeDigest.of(new byte[32]);
        MfaRecoveryCode recoveryCode = MfaRecoveryCode.rehydrate(
            UUID.randomUUID(),
            USER_ID,
            digest,
            NOW.minusSeconds(60),
            NOW.minusSeconds(1)
        );
        when(recoveryCodeDigest.digest(RECOVERY_CODE))
            .thenReturn(digest);
        when(recoveryCodeRepository.findByUserIdAndDigestForUpdate(
            USER_ID,
            digest
        )).thenReturn(Optional.of(recoveryCode));

        assertThat(verifier.verifyAndConsume(
            USER_ID,
            authenticator,
            RECOVERY_CODE,
            NOW
        )).isFalse();

        verify(recoveryCodeRepository, never()).save(any());
    }

    @Test
    void shouldRejectUnknownOrMalformedProofWithoutLeakingProofType() {
        MfaRecoveryCodeDigest digest =
            MfaRecoveryCodeDigest.of(new byte[32]);
        when(recoveryCodeDigest.digest(RECOVERY_CODE))
            .thenReturn(digest);
        when(recoveryCodeRepository.findByUserIdAndDigestForUpdate(
            USER_ID,
            digest
        )).thenReturn(Optional.empty());

        assertThat(verifier.verifyAndConsume(
            USER_ID,
            authenticator,
            RECOVERY_CODE,
            NOW
        )).isFalse();
        assertThat(verifier.verifyAndConsume(
            USER_ID,
            authenticator,
            "malformed",
            NOW
        )).isFalse();
    }

    @Test
    void shouldFailClosedWhenTotpSecretCannotBeRevealed() {
        when(secretProtection.reveal(
            USER_ID,
            authenticator.protectedSecret()
        )).thenThrow(new MfaSecretProtectionFailureException());

        assertThatThrownBy(() -> verifier.verifyAndConsume(
            USER_ID,
            authenticator,
            "123456",
            NOW
        )).isInstanceOf(MfaSecurityUnavailableException.class);
    }
}

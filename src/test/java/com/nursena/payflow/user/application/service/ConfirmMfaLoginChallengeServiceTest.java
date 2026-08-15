package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.abuseprotection.application.exception.AbuseProtectionUnavailableException;
import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionFailureMode;
import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionWorkflow;
import com.nursena.payflow.abuseprotection.application.port.out.AbuseProtectionDecision;
import com.nursena.payflow.abuseprotection.application.port.out.AbuseProtectionDimension;
import com.nursena.payflow.abuseprotection.application.port.out.AbuseProtectionEnforcementPort;
import com.nursena.payflow.clientcontext.domain.IpAddress;
import com.nursena.payflow.user.application.exception.MfaSecurityUnavailableException;
import com.nursena.payflow.user.application.port.in.AuthenticatedUserResult;
import com.nursena.payflow.user.application.port.in.ConfirmMfaLoginChallengeCommand;
import com.nursena.payflow.user.application.port.out.MfaAuthenticatorRepositoryPort;
import com.nursena.payflow.user.application.port.out.MfaLoginChallengeDigestPort;
import com.nursena.payflow.user.application.port.out.MfaLoginChallengeRepositoryPort;
import com.nursena.payflow.user.application.port.out.UserRepositoryPort;
import com.nursena.payflow.user.domain.exception.InvalidMfaLoginChallengeException;
import com.nursena.payflow.user.domain.model.EmailAddress;
import com.nursena.payflow.user.domain.model.MfaAuthenticator;
import com.nursena.payflow.user.domain.model.MfaLifecycleState;
import com.nursena.payflow.user.domain.model.MfaLoginChallenge;
import com.nursena.payflow.user.domain.model.MfaLoginChallengeDigest;
import com.nursena.payflow.user.domain.model.MfaLoginChallengeState;
import com.nursena.payflow.user.domain.model.ProtectedMfaSecret;
import com.nursena.payflow.user.domain.model.User;
import com.nursena.payflow.user.domain.model.UserRole;
import com.nursena.payflow.user.domain.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class ConfirmMfaLoginChallengeServiceTest {

    private static final UUID USER_ID =
        UUID.fromString("76bf88a0-8524-43ff-ac1a-c77547d89e43");
    private static final UUID CHALLENGE_ID =
        UUID.fromString("8db7ea3b-b29a-4f1d-bf4a-30d250c7278b");
    private static final Instant NOW =
        Instant.parse("2026-08-08T12:00:00Z");
    private static final MfaLoginChallengeDigest DIGEST =
        MfaLoginChallengeDigest.of(new byte[32]);
    private static final String ABUSE_IDENTITY = "0".repeat(64);
    private static final String MALFORMED_ABUSE_IDENTITY =
        "f".repeat(64);

    @Mock MfaLoginChallengeDigestPort digestPort;
    @Mock AbuseProtectionEnforcementPort abuseProtection;
    @Mock MfaLoginChallengeRepositoryPort challengeRepository;
    @Mock UserRepositoryPort userRepository;
    @Mock MfaAuthenticatorRepositoryPort authenticatorRepository;
    @Mock MfaLoginSecondFactorVerifier secondFactorVerifier;
    @Mock AuthenticationCredentialIssuer credentialIssuer;

    private ConfirmMfaLoginChallengeService service;
    private User user;

    @BeforeEach
    void setUp() {
        lenient().when(abuseProtection.evaluate(any()))
            .thenReturn(AbuseProtectionDecision.allowed());
        service = new ConfirmMfaLoginChallengeService(
            digestPort,
            abuseProtection,
            challengeRepository,
            userRepository,
            authenticatorRepository,
            secondFactorVerifier,
            credentialIssuer,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        user = User.rehydrate(
            USER_ID,
            EmailAddress.of("user@example.com"),
            "hash",
            UserRole.USER,
            UserStatus.ACTIVE,
            NOW,
            NOW.minusSeconds(60),
            NOW.minusSeconds(60)
        );
    }

    @Test
    void shouldEnforceAbuseProtectionBeforeChallengeLookup() {
        when(digestPort.digest("unknown")).thenReturn(DIGEST);
        when(challengeRepository.findUserIdByDigest(DIGEST))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(
            command("unknown", "123456")
        )).isInstanceOf(InvalidMfaLoginChallengeException.class);

        InOrder order = inOrder(abuseProtection, challengeRepository);
        order.verify(abuseProtection).evaluate(argThat(request ->
            request.workflow()
                    == AbuseProtectionWorkflow.MFA_LOGIN_CHALLENGE_CONFIRMATION
                && request.normalizedIdentity().equals(ABUSE_IDENTITY)
                && request.effectiveClientAddress().equals(
                    IpAddress.parse("203.0.113.10")
                )
        ));
        order.verify(challengeRepository).findUserIdByDigest(DIGEST);
    }

    @Test
    void shouldRejectBlockedChallengeBeforeSensitiveStateAccess() {
        when(digestPort.digest("challenge")).thenReturn(DIGEST);
        when(abuseProtection.evaluate(any()))
            .thenReturn(AbuseProtectionDecision.blocked(
                AbuseProtectionDimension.IDENTITY,
                Duration.ofSeconds(30)
            ));

        assertThatThrownBy(() -> service.confirm(
            command("challenge", "123456")
        )).isInstanceOf(InvalidMfaLoginChallengeException.class);

        verifyNoInteractions(
            challengeRepository,
            userRepository,
            authenticatorRepository,
            secondFactorVerifier,
            credentialIssuer
        );
    }

    @Test
    void shouldFailClosedBeforeSensitiveStateAccessWhenAbuseProtectionIsUnavailable() {
        when(digestPort.digest("challenge")).thenReturn(DIGEST);
        when(abuseProtection.evaluate(any()))
            .thenThrow(new AbuseProtectionUnavailableException(
                AbuseProtectionWorkflow.MFA_LOGIN_CHALLENGE_CONFIRMATION,
                AbuseProtectionFailureMode.FAIL_CLOSED,
                new IllegalStateException("redis unavailable")
            ));

        assertThatThrownBy(() -> service.confirm(
            command("challenge", "123456")
        )).isInstanceOf(MfaSecurityUnavailableException.class);

        verifyNoInteractions(
            challengeRepository,
            userRepository,
            authenticatorRepository,
            secondFactorVerifier,
            credentialIssuer
        );
    }

    @Test
    void shouldConsumeValidSecondFactorChallengeBeforeIssuingCredentials() {
        MfaLoginChallenge challenge = pending(
            5,
            NOW.plusSeconds(300)
        );
        MfaAuthenticator authenticator = stubCandidate(challenge);
        when(secondFactorVerifier.verifyAndConsume(
            USER_ID,
            authenticator,
            "123456",
            NOW
        )).thenReturn(true);
        AuthenticatedUserResult credentials = credentials();
        when(credentialIssuer.issue(user, NOW)).thenReturn(credentials);

        assertThat(service.confirm(command("challenge", "123456")))
            .isSameAs(credentials);
        verify(challengeRepository).save(
            org.mockito.ArgumentMatchers.argThat(saved ->
                saved.state() == MfaLoginChallengeState.CONSUMED
            )
        );
        verify(credentialIssuer).issue(user, NOW);
    }

    @Test
    void shouldDecrementAttemptAndReturnGenericFailureForInvalidProof() {
        MfaLoginChallenge challenge = pending(
            5,
            NOW.plusSeconds(300)
        );
        MfaAuthenticator authenticator = stubCandidate(challenge);
        when(secondFactorVerifier.verifyAndConsume(
            USER_ID,
            authenticator,
            "invalid-proof",
            NOW
        )).thenReturn(false);

        assertThatThrownBy(() -> service.confirm(
            command("challenge", "invalid-proof")
        ))
            .isInstanceOf(InvalidMfaLoginChallengeException.class)
            .hasMessage(
                "The MFA challenge or proof could not be verified."
            );
        verify(challengeRepository).save(
            org.mockito.ArgumentMatchers.argThat(saved ->
                saved.state() == MfaLoginChallengeState.PENDING
                    && saved.attemptsRemaining() == 4
            )
        );
        verifyNoInteractions(credentialIssuer);
    }

    @Test
    void shouldExhaustFinalAttempt() {
        MfaLoginChallenge challenge = pending(
            1,
            NOW.plusSeconds(300)
        );
        MfaAuthenticator authenticator = stubCandidate(challenge);
        when(secondFactorVerifier.verifyAndConsume(
            USER_ID,
            authenticator,
            "000000",
            NOW
        )).thenReturn(false);

        assertThatThrownBy(() -> service.confirm(
            command("challenge", "000000")
        )).isInstanceOf(InvalidMfaLoginChallengeException.class);
        verify(challengeRepository).save(
            org.mockito.ArgumentMatchers.argThat(saved ->
                saved.state() == MfaLoginChallengeState.EXHAUSTED
                    && saved.attemptsRemaining() == 0
            )
        );
    }

    @Test
    void shouldPersistExpirationBeforeGenericFailure() {
        MfaLoginChallenge challenge = pending(5, NOW);
        when(digestPort.digest("challenge")).thenReturn(DIGEST);
        when(challengeRepository.findUserIdByDigest(DIGEST))
            .thenReturn(Optional.of(USER_ID));
        when(userRepository.findByIdForUpdate(USER_ID))
            .thenReturn(Optional.of(user));
        when(challengeRepository.findByDigestForUpdate(DIGEST))
            .thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> service.confirm(
            command("challenge", "123456")
        )).isInstanceOf(InvalidMfaLoginChallengeException.class);
        verify(challengeRepository).save(
            org.mockito.ArgumentMatchers.argThat(saved ->
                saved.state() == MfaLoginChallengeState.EXPIRED
            )
        );
        verifyNoInteractions(
            authenticatorRepository,
            secondFactorVerifier,
            credentialIssuer
        );
    }

    @Test
    void shouldRejectConsumedChallengeWithoutReissuingCredentials() {
        MfaLoginChallenge consumed = pending(
            5,
            NOW.plusSeconds(300)
        ).consume(NOW.minusSeconds(1));
        when(digestPort.digest("challenge")).thenReturn(DIGEST);
        when(challengeRepository.findUserIdByDigest(DIGEST))
            .thenReturn(Optional.of(USER_ID));
        when(userRepository.findByIdForUpdate(USER_ID))
            .thenReturn(Optional.of(user));
        when(challengeRepository.findByDigestForUpdate(DIGEST))
            .thenReturn(Optional.of(consumed));

        assertThatThrownBy(() -> service.confirm(
            command("challenge", "123456")
        )).isInstanceOf(InvalidMfaLoginChallengeException.class);
        verifyNoInteractions(secondFactorVerifier, credentialIssuer);
    }

    @Test
    void shouldRejectUnknownChallengeWithoutLookingUpUser() {
        when(digestPort.digest("unknown")).thenReturn(DIGEST);
        when(challengeRepository.findUserIdByDigest(DIGEST))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(
            command("unknown", "123456")
        )).isInstanceOf(InvalidMfaLoginChallengeException.class);
        verifyNoInteractions(
            userRepository,
            secondFactorVerifier,
            credentialIssuer
        );
    }

    @Test
    void shouldRejectMalformedChallengeThroughSamePublicException() {
        assertThatThrownBy(() -> service.confirm(
            command(" ", "123456")
        )).isInstanceOf(InvalidMfaLoginChallengeException.class);

        verify(abuseProtection).evaluate(argThat(request ->
            request.workflow()
                    == AbuseProtectionWorkflow.MFA_LOGIN_CHALLENGE_CONFIRMATION
                && request.normalizedIdentity().equals(
                    MALFORMED_ABUSE_IDENTITY
                )
        ));
        verifyNoInteractions(
            digestPort,
            challengeRepository,
            userRepository,
            authenticatorRepository,
            secondFactorVerifier,
            credentialIssuer
        );
    }

    @Test
    void shouldFailClosedForMalformedChallengeBeforeStateAccess() {
        when(abuseProtection.evaluate(any()))
            .thenThrow(new AbuseProtectionUnavailableException(
                AbuseProtectionWorkflow.MFA_LOGIN_CHALLENGE_CONFIRMATION,
                AbuseProtectionFailureMode.FAIL_CLOSED,
                new IllegalStateException("redis unavailable")
            ));

        assertThatThrownBy(() -> service.confirm(
            command(" ", "123456")
        )).isInstanceOf(MfaSecurityUnavailableException.class);

        verifyNoInteractions(
            digestPort,
            challengeRepository,
            userRepository,
            authenticatorRepository,
            secondFactorVerifier,
            credentialIssuer
        );
    }

    @Test
    void shouldRequireEnabledAuthenticatorAtVerificationTime() {
        MfaLoginChallenge challenge = pending(
            5,
            NOW.plusSeconds(300)
        );
        when(digestPort.digest("challenge")).thenReturn(DIGEST);
        when(challengeRepository.findUserIdByDigest(DIGEST))
            .thenReturn(Optional.of(USER_ID));
        when(userRepository.findByIdForUpdate(USER_ID))
            .thenReturn(Optional.of(user));
        when(challengeRepository.findByDigestForUpdate(DIGEST))
            .thenReturn(Optional.of(challenge));
        when(authenticatorRepository.findByUserIdForUpdate(USER_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(
            command("challenge", "123456")
        )).isInstanceOf(InvalidMfaLoginChallengeException.class);
        verifyNoInteractions(secondFactorVerifier, credentialIssuer);
    }

    @Test
    void shouldUseNoRollbackForGenericChallengeFailure()
        throws Exception {
        Method method = ConfirmMfaLoginChallengeService.class
            .getDeclaredMethod(
                "confirm",
                ConfirmMfaLoginChallengeCommand.class
            );
        Transactional transactional =
            method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.noRollbackFor())
            .containsExactly(
                InvalidMfaLoginChallengeException.class
            );
    }

    private MfaAuthenticator stubCandidate(
        MfaLoginChallenge challenge
    ) {
        MfaAuthenticator authenticator = enabledAuthenticator();
        when(digestPort.digest("challenge")).thenReturn(DIGEST);
        when(challengeRepository.findUserIdByDigest(DIGEST))
            .thenReturn(Optional.of(USER_ID));
        when(userRepository.findByIdForUpdate(USER_ID))
            .thenReturn(Optional.of(user));
        when(challengeRepository.findByDigestForUpdate(DIGEST))
            .thenReturn(Optional.of(challenge));
        when(authenticatorRepository.findByUserIdForUpdate(USER_ID))
            .thenReturn(Optional.of(authenticator));
        return authenticator;
    }

    private static ConfirmMfaLoginChallengeCommand command(
        String token,
        String code
    ) {
        return new ConfirmMfaLoginChallengeCommand(
            token,
            code,
            IpAddress.parse("203.0.113.10")
        );
    }

    private static MfaLoginChallenge pending(
        int attempts,
        Instant expiresAt
    ) {
        return MfaLoginChallenge.issue(
            CHALLENGE_ID,
            USER_ID,
            DIGEST,
            NOW.minusSeconds(60),
            expiresAt,
            attempts
        );
    }

    private static MfaAuthenticator enabledAuthenticator() {
        return MfaAuthenticator.rehydrate(
            USER_ID,
            MfaLifecycleState.ENABLED,
            ProtectedMfaSecret.of(new byte[49]),
            null,
            NOW.minusSeconds(120),
            NOW.minusSeconds(180),
            NOW.minusSeconds(120)
        );
    }

    private static AuthenticatedUserResult credentials() {
        return new AuthenticatedUserResult(
            "access",
            NOW.plusSeconds(900),
            "refresh",
            NOW.plusSeconds(3600)
        );
    }
}

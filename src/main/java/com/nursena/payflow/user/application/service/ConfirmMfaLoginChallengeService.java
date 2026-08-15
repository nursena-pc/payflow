package com.nursena.payflow.user.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.abuseprotection.application.exception.AbuseProtectionUnavailableException;
import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionWorkflow;
import com.nursena.payflow.abuseprotection.application.port.out.AbuseProtectionDecision;
import com.nursena.payflow.abuseprotection.application.port.out.AbuseProtectionEnforcementPort;
import com.nursena.payflow.abuseprotection.application.port.out.AbuseProtectionRequest;
import com.nursena.payflow.clientcontext.domain.IpAddress;
import com.nursena.payflow.user.application.exception.MfaSecurityUnavailableException;
import com.nursena.payflow.user.application.port.in.AuthenticatedUserResult;
import com.nursena.payflow.user.application.port.in.ConfirmMfaLoginChallengeCommand;
import com.nursena.payflow.user.application.port.in.ConfirmMfaLoginChallengeUseCase;
import com.nursena.payflow.user.application.port.out.MfaAuthenticatorRepositoryPort;
import com.nursena.payflow.user.application.port.out.MfaLoginChallengeDigestPort;
import com.nursena.payflow.user.application.port.out.MfaLoginChallengeRepositoryPort;
import com.nursena.payflow.user.application.port.out.UserRepositoryPort;
import com.nursena.payflow.user.domain.exception.InvalidMfaLoginChallengeException;
import com.nursena.payflow.user.domain.model.MfaAuthenticator;
import com.nursena.payflow.user.domain.model.MfaLifecycleState;
import com.nursena.payflow.user.domain.model.MfaLoginChallenge;
import com.nursena.payflow.user.domain.model.MfaLoginChallengeDigest;
import com.nursena.payflow.user.domain.model.MfaLoginChallengeState;
import com.nursena.payflow.user.domain.model.User;
import com.nursena.payflow.user.domain.model.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfirmMfaLoginChallengeService
    implements ConfirmMfaLoginChallengeUseCase {

    private static final MfaLoginChallengeDigest
        MALFORMED_CHALLENGE_ABUSE_DIGEST =
            MfaLoginChallengeDigest.of(
                HexFormat.of().parseHex("f".repeat(64))
            );

    private final MfaLoginChallengeDigestPort digestPort;
    private final AbuseProtectionEnforcementPort abuseProtection;
    private final MfaLoginChallengeRepositoryPort challengeRepository;
    private final UserRepositoryPort userRepository;
    private final MfaAuthenticatorRepositoryPort authenticatorRepository;
    private final MfaSecondFactorVerifier secondFactorVerifier;
    private final AuthenticationCredentialIssuer credentialIssuer;
    private final Clock clock;

    public ConfirmMfaLoginChallengeService(
        MfaLoginChallengeDigestPort digestPort,
        AbuseProtectionEnforcementPort abuseProtection,
        MfaLoginChallengeRepositoryPort challengeRepository,
        UserRepositoryPort userRepository,
        MfaAuthenticatorRepositoryPort authenticatorRepository,
        MfaSecondFactorVerifier secondFactorVerifier,
        AuthenticationCredentialIssuer credentialIssuer,
        Clock clock
    ) {
        this.digestPort = digestPort;
        this.abuseProtection = abuseProtection;
        this.challengeRepository = challengeRepository;
        this.userRepository = userRepository;
        this.authenticatorRepository = authenticatorRepository;
        this.secondFactorVerifier = secondFactorVerifier;
        this.credentialIssuer = credentialIssuer;
        this.clock = clock;
    }

    @Override
    @Transactional(noRollbackFor = InvalidMfaLoginChallengeException.class)
    public AuthenticatedUserResult confirm(
        ConfirmMfaLoginChallengeCommand command
    ) {
        ConfirmMfaLoginChallengeCommand checkedCommand =
            Objects.requireNonNull(command, "command must not be null");
        ChallengeDigestResolution digestResolution =
            resolveChallengeDigest(
                checkedCommand.challengeToken()
            );
        enforceAbuseProtection(
            digestResolution.digest(),
            checkedCommand.effectiveClientAddress()
        );
        if (!digestResolution.usableForLookup()) {
            throw new InvalidMfaLoginChallengeException();
        }

        MfaLoginChallengeDigest digest =
            digestResolution.digest();
        UUID candidateUserId = challengeRepository
            .findUserIdByDigest(digest)
            .orElseThrow(InvalidMfaLoginChallengeException::new);

        User user = userRepository
            .findByIdForUpdate(candidateUserId)
            .orElseThrow(InvalidMfaLoginChallengeException::new);
        MfaLoginChallenge challenge = challengeRepository
            .findByDigestForUpdate(digest)
            .filter(value -> value.userId().equals(candidateUserId))
            .orElseThrow(InvalidMfaLoginChallengeException::new);

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (challenge.state() == MfaLoginChallengeState.PENDING
            && !challenge.expiresAt().isAfter(now)) {
            challengeRepository.save(challenge.expire(now));
            throw new InvalidMfaLoginChallengeException();
        }
        if (!challenge.isPendingAt(now) || !isEligible(user)) {
            throw new InvalidMfaLoginChallengeException();
        }

        MfaAuthenticator authenticator = authenticatorRepository
            .findByUserIdForUpdate(user.id())
            .filter(value -> value.state() == MfaLifecycleState.ENABLED)
            .orElseThrow(InvalidMfaLoginChallengeException::new);

        boolean verified = secondFactorVerifier.verifyAndConsume(
            user.id(),
            authenticator,
            checkedCommand.code(),
            now
        );

        if (!verified) {
            challengeRepository.save(challenge.failAttempt(now));
            throw new InvalidMfaLoginChallengeException();
        }

        challengeRepository.save(challenge.consume(now));
        return credentialIssuer.issue(user, now);
    }

    private void enforceAbuseProtection(
        MfaLoginChallengeDigest digest,
        IpAddress effectiveClientAddress
    ) {
        try {
            AbuseProtectionDecision decision =
                abuseProtection.evaluate(
                    new AbuseProtectionRequest(
                        AbuseProtectionWorkflow
                            .MFA_LOGIN_CHALLENGE_CONFIRMATION,
                        HexFormat.of().formatHex(digest.value()),
                        effectiveClientAddress
                    )
                );

            if (!decision.isAllowed()) {
                throw new InvalidMfaLoginChallengeException();
            }
        }
        catch (AbuseProtectionUnavailableException exception) {
            throw new MfaSecurityUnavailableException();
        }
    }

    private ChallengeDigestResolution resolveChallengeDigest(
        String value
    ) {
        if (value == null || value.isBlank() || value.length() > 256) {
            return ChallengeDigestResolution.malformed();
        }
        try {
            return ChallengeDigestResolution.usable(
                digestPort.digest(value)
            );
        }
        catch (IllegalArgumentException exception) {
            return ChallengeDigestResolution.malformed();
        }
    }

    private static boolean isEligible(User user) {
        return user.status() == UserStatus.ACTIVE && user.isEmailVerified();
    }

    private record ChallengeDigestResolution(
        MfaLoginChallengeDigest digest,
        boolean usableForLookup
    ) {
        private static ChallengeDigestResolution usable(
            MfaLoginChallengeDigest digest
        ) {
            return new ChallengeDigestResolution(
                Objects.requireNonNull(
                    digest,
                    "digest must not be null"
                ),
                true
            );
        }

        private static ChallengeDigestResolution malformed() {
            return new ChallengeDigestResolution(
                MALFORMED_CHALLENGE_ABUSE_DIGEST,
                false
            );
        }
    }
}

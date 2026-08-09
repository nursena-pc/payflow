package com.nursena.payflow.user.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

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

    private final MfaLoginChallengeDigestPort digestPort;
    private final MfaLoginChallengeRepositoryPort challengeRepository;
    private final UserRepositoryPort userRepository;
    private final MfaAuthenticatorRepositoryPort authenticatorRepository;
    private final MfaLoginSecondFactorVerifier secondFactorVerifier;
    private final AuthenticationCredentialIssuer credentialIssuer;
    private final Clock clock;

    public ConfirmMfaLoginChallengeService(
        MfaLoginChallengeDigestPort digestPort,
        MfaLoginChallengeRepositoryPort challengeRepository,
        UserRepositoryPort userRepository,
        MfaAuthenticatorRepositoryPort authenticatorRepository,
        MfaLoginSecondFactorVerifier secondFactorVerifier,
        AuthenticationCredentialIssuer credentialIssuer,
        Clock clock
    ) {
        this.digestPort = digestPort;
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
        MfaLoginChallengeDigest digest = digestSafely(
            checkedCommand.challengeToken()
        );
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

    private MfaLoginChallengeDigest digestSafely(String value) {
        if (value == null || value.isBlank() || value.length() > 256) {
            throw new InvalidMfaLoginChallengeException();
        }
        try {
            return digestPort.digest(value);
        }
        catch (IllegalArgumentException exception) {
            throw new InvalidMfaLoginChallengeException();
        }
    }

    private static boolean isEligible(User user) {
        return user.status() == UserStatus.ACTIVE && user.isEmailVerified();
    }
}

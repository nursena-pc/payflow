package com.nursena.payflow.user.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.nursena.payflow.user.application.exception.LoginRateLimitExceededException;
import com.nursena.payflow.user.application.port.in.AuthenticateUserCommand;
import com.nursena.payflow.user.application.port.in.AuthenticateUserResult;
import com.nursena.payflow.user.application.port.in.AuthenticateUserUseCase;
import com.nursena.payflow.user.application.port.out.LoginRateLimitDecision;
import com.nursena.payflow.user.application.port.out.LoginRateLimitPort;
import com.nursena.payflow.user.application.port.out.LoginRateLimitRequest;
import com.nursena.payflow.user.application.port.out.MfaAuthenticatorRepositoryPort;
import com.nursena.payflow.user.application.port.out.PasswordVerificationPort;
import com.nursena.payflow.user.application.port.out.UserRepositoryPort;
import com.nursena.payflow.user.domain.exception.InvalidCredentialsException;
import com.nursena.payflow.user.domain.exception.UserAccountUnavailableException;
import com.nursena.payflow.user.domain.model.EmailAddress;
import com.nursena.payflow.user.domain.model.MfaLifecycleState;
import com.nursena.payflow.user.domain.model.User;
import com.nursena.payflow.user.domain.model.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticateUserService implements AuthenticateUserUseCase {

    private final UserRepositoryPort userRepository;
    private final PasswordVerificationPort passwordVerification;
    private final MfaAuthenticatorRepositoryPort authenticatorRepository;
    private final AuthenticationCredentialIssuer credentialIssuer;
    private final MfaLoginChallengeIssuer challengeIssuer;
    private final LoginRateLimitPort loginRateLimit;
    private final Clock clock;

    public AuthenticateUserService(
        UserRepositoryPort userRepository,
        PasswordVerificationPort passwordVerification,
        MfaAuthenticatorRepositoryPort authenticatorRepository,
        AuthenticationCredentialIssuer credentialIssuer,
        MfaLoginChallengeIssuer challengeIssuer,
        LoginRateLimitPort loginRateLimit,
        Clock clock
    ) {
        this.userRepository = Objects.requireNonNull(
            userRepository,
            "userRepository must not be null"
        );
        this.passwordVerification = Objects.requireNonNull(
            passwordVerification,
            "passwordVerification must not be null"
        );
        this.authenticatorRepository = Objects.requireNonNull(
            authenticatorRepository,
            "authenticatorRepository must not be null"
        );
        this.credentialIssuer = Objects.requireNonNull(
            credentialIssuer,
            "credentialIssuer must not be null"
        );
        this.challengeIssuer = Objects.requireNonNull(
            challengeIssuer,
            "challengeIssuer must not be null"
        );
        this.loginRateLimit = Objects.requireNonNull(
            loginRateLimit,
            "loginRateLimit must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    @Transactional
    public AuthenticateUserResult authenticate(AuthenticateUserCommand command) {
        AuthenticateUserCommand validatedCommand = Objects.requireNonNull(
            command,
            "command must not be null"
        );
        EmailAddress email = EmailAddress.of(validatedCommand.email());
        enforceRateLimit(email, validatedCommand.clientAddress());

        User observedUser = userRepository.findByEmail(email)
            .orElseThrow(InvalidCredentialsException::new);
        requirePassword(validatedCommand.rawPassword(), observedUser);

        User user = userRepository.findByIdForUpdate(observedUser.id())
            .orElseThrow(InvalidCredentialsException::new);
        if (!user.passwordHash().equals(observedUser.passwordHash())) {
            requirePassword(validatedCommand.rawPassword(), user);
        }
        requireEligible(user);

        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        boolean mfaEnabled = authenticatorRepository
            .findByUserIdForUpdate(user.id())
            .map(value -> value.state() == MfaLifecycleState.ENABLED)
            .orElse(false);

        AuthenticateUserResult result = mfaEnabled
            ? challengeIssuer.issue(user.id(), issuedAt)
            : credentialIssuer.issue(user, issuedAt);

        loginRateLimit.resetIdentity(email);
        return result;
    }

    private void requirePassword(String rawPassword, User user) {
        if (!passwordVerification.matches(rawPassword, user.passwordHash())) {
            throw new InvalidCredentialsException();
        }
    }

    private static void requireEligible(User user) {
        if (user.status() != UserStatus.ACTIVE || !user.isEmailVerified()) {
            throw new UserAccountUnavailableException();
        }
    }

    private void enforceRateLimit(EmailAddress email, String clientAddress) {
        LoginRateLimitDecision decision = loginRateLimit.evaluate(
            new LoginRateLimitRequest(email, clientAddress)
        );
        if (!decision.isAllowed()) {
            throw new LoginRateLimitExceededException(
                decision.blockedDimension(),
                decision.retryAfter()
            );
        }
    }
}

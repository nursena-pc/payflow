
package com.nursena.payflow.user.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.user.application.exception
    .LoginRateLimitExceededException;
import com.nursena.payflow.user.application.port.in
    .AuthenticateUserCommand;
import com.nursena.payflow.user.application.port.in
    .AuthenticateUserResult;
import com.nursena.payflow.user.application.port.in
    .AuthenticateUserUseCase;
import com.nursena.payflow.user.application.port.out
    .AccessTokenGenerationPort;
import com.nursena.payflow.user.application.port.out
    .GeneratedAccessToken;
import com.nursena.payflow.user.application.port.out
    .GeneratedRefreshToken;
import com.nursena.payflow.user.application.port.out
    .LoginRateLimitDecision;
import com.nursena.payflow.user.application.port.out
    .LoginRateLimitPort;
import com.nursena.payflow.user.application.port.out
    .LoginRateLimitRequest;
import com.nursena.payflow.user.application.port.out
    .PasswordVerificationPort;
import com.nursena.payflow.user.application.port.out
    .RefreshTokenDigestPort;
import com.nursena.payflow.user.application.port.out
    .RefreshTokenFamilyRepositoryPort;
import com.nursena.payflow.user.application.port.out
    .RefreshTokenGenerationPort;
import com.nursena.payflow.user.application.port.out
    .RefreshTokenRecordRepositoryPort;
import com.nursena.payflow.user.application.port.out
    .UserRepositoryPort;
import com.nursena.payflow.user.domain.exception
    .InvalidCredentialsException;
import com.nursena.payflow.user.domain.exception
    .UserAccountUnavailableException;
import com.nursena.payflow.user.domain.model.EmailAddress;
import com.nursena.payflow.user.domain.model
    .RefreshTokenDigest;
import com.nursena.payflow.user.domain.model
    .RefreshTokenFamily;
import com.nursena.payflow.user.domain.model
    .RefreshTokenFamilyId;
import com.nursena.payflow.user.domain.model
    .RefreshTokenRecord;
import com.nursena.payflow.user.domain.model
    .RefreshTokenRecordId;
import com.nursena.payflow.user.domain.model.User;
import com.nursena.payflow.user.domain.model
    .UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation
    .Transactional;

@Service
public class AuthenticateUserService
    implements AuthenticateUserUseCase {

    private final UserRepositoryPort userRepository;

    private final PasswordVerificationPort
        passwordVerification;

    private final RefreshTokenGenerationPort
        refreshTokenGeneration;

    private final RefreshTokenDigestPort
        refreshTokenDigest;

    private final RefreshTokenFamilyRepositoryPort
        familyRepository;

    private final RefreshTokenRecordRepositoryPort
        recordRepository;

    private final AccessTokenGenerationPort
        accessTokenGeneration;

    private final LoginRateLimitPort loginRateLimit;

    private final RefreshSessionLifetimePolicy
        lifetimePolicy;

    private final Clock clock;

    public AuthenticateUserService(
        UserRepositoryPort userRepository,
        PasswordVerificationPort passwordVerification,
        RefreshTokenGenerationPort refreshTokenGeneration,
        RefreshTokenDigestPort refreshTokenDigest,
        RefreshTokenFamilyRepositoryPort familyRepository,
        RefreshTokenRecordRepositoryPort recordRepository,
        AccessTokenGenerationPort accessTokenGeneration,
        LoginRateLimitPort loginRateLimit,
        RefreshSessionLifetimePolicy lifetimePolicy,
        Clock clock
    ) {
        this.userRepository =
            Objects.requireNonNull(
                userRepository,
                "userRepository must not be null"
            );

        this.passwordVerification =
            Objects.requireNonNull(
                passwordVerification,
                "passwordVerification must not be null"
            );

        this.refreshTokenGeneration =
            Objects.requireNonNull(
                refreshTokenGeneration,
                "refreshTokenGeneration must not be null"
            );

        this.refreshTokenDigest =
            Objects.requireNonNull(
                refreshTokenDigest,
                "refreshTokenDigest must not be null"
            );

        this.familyRepository =
            Objects.requireNonNull(
                familyRepository,
                "familyRepository must not be null"
            );

        this.recordRepository =
            Objects.requireNonNull(
                recordRepository,
                "recordRepository must not be null"
            );

        this.accessTokenGeneration =
            Objects.requireNonNull(
                accessTokenGeneration,
                "accessTokenGeneration must not be null"
            );

        this.loginRateLimit =
            Objects.requireNonNull(
                loginRateLimit,
                "loginRateLimit must not be null"
            );

        this.lifetimePolicy =
            Objects.requireNonNull(
                lifetimePolicy,
                "lifetimePolicy must not be null"
            );

        this.clock =
            Objects.requireNonNull(
                clock,
                "clock must not be null"
            );
    }

    @Override
    @Transactional
    public AuthenticateUserResult authenticate(
        AuthenticateUserCommand command
    ) {
        AuthenticateUserCommand validatedCommand =
            Objects.requireNonNull(
                command,
                "command must not be null"
            );

        EmailAddress email =
            EmailAddress.of(
                validatedCommand.email()
            );

        enforceRateLimit(
            email,
            validatedCommand.clientAddress()
        );

        User user =
            userRepository
                .findByEmail(email)
                .orElseThrow(
                    InvalidCredentialsException::new
                );

        boolean passwordMatches =
            passwordVerification.matches(
                validatedCommand.rawPassword(),
                user.passwordHash()
            );

        if (!passwordMatches) {
            throw new InvalidCredentialsException();
        }

        if (
            user.status() != UserStatus.ACTIVE
                || !user.isEmailVerified()
        ) {
            throw new
                UserAccountUnavailableException();
        }

        Instant issuedAt =
            clock.instant()
                .truncatedTo(
                    ChronoUnit.MICROS
                );

        GeneratedRefreshToken
            generatedRefreshToken =
            refreshTokenGeneration.generate();

        RefreshTokenDigest digest =
            refreshTokenDigest.digest(
                generatedRefreshToken.value()
            );

        Instant familyExpiresAt =
            lifetimePolicy.familyExpiresAt(
                issuedAt
            );

        RefreshTokenFamily family =
            RefreshTokenFamily.create(
                RefreshTokenFamilyId.of(
                    UUID.randomUUID()
                ),
                user.id(),
                issuedAt,
                familyExpiresAt
            );

        RefreshTokenFamily savedFamily =
            familyRepository.save(
                family
            );

        Instant refreshTokenExpiresAt =
            lifetimePolicy.refreshTokenExpiresAt(
                issuedAt,
                savedFamily.expiresAt()
            );

        RefreshTokenRecord record =
            RefreshTokenRecord.issue(
                RefreshTokenRecordId.of(
                    UUID.randomUUID()
                ),
                savedFamily,
                digest,
                issuedAt,
                refreshTokenExpiresAt
            );

        RefreshTokenRecord savedRecord =
            recordRepository.save(
                record
            );

        GeneratedAccessToken accessToken =
            accessTokenGeneration.generate(
                user
            );

        loginRateLimit.resetIdentity(email);

        return new AuthenticateUserResult(
            accessToken.value(),
            accessToken.expiresAt(),
            generatedRefreshToken.value(),
            savedRecord.expiresAt()
        );
    }

    private void enforceRateLimit(
        EmailAddress email,
        String clientAddress
    ) {
        LoginRateLimitDecision decision =
            loginRateLimit.evaluate(
                new LoginRateLimitRequest(
                    email,
                    clientAddress
                )
            );

        if (!decision.isAllowed()) {
            throw new
                LoginRateLimitExceededException(
                decision.blockedDimension(),
                decision.retryAfter()
            );
        }
    }
}

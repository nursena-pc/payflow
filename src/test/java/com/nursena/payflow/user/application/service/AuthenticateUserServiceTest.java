package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.user.application.exception
    .LoginRateLimitExceededException;
import com.nursena.payflow.user.application.exception
    .LoginRateLimitUnavailableException;
import com.nursena.payflow.user.application.port.in.AuthenticateUserCommand;
import com.nursena.payflow.user.application.port.in.AuthenticateUserResult;
import com.nursena.payflow.user.application.port.out.AccessTokenGenerationPort;
import com.nursena.payflow.user.application.port.out.GeneratedAccessToken;
import com.nursena.payflow.user.application.port.out.GeneratedRefreshToken;
import com.nursena.payflow.user.application.port.out.LoginRateLimitDecision;
import com.nursena.payflow.user.application.port.out.LoginRateLimitDimension;
import com.nursena.payflow.user.application.port.out.LoginRateLimitPort;
import com.nursena.payflow.user.application.port.out.LoginRateLimitRequest;
import com.nursena.payflow.user.application.port.out.PasswordVerificationPort;
import com.nursena.payflow.user.application.port.out.RefreshTokenDigestPort;
import com.nursena.payflow.user.application.port.out.RefreshTokenFamilyRepositoryPort;
import com.nursena.payflow.user.application.port.out.RefreshTokenGenerationPort;
import com.nursena.payflow.user.application.port.out.RefreshTokenRecordRepositoryPort;
import com.nursena.payflow.user.application.port.out.UserRepositoryPort;
import com.nursena.payflow.user.domain.exception.InvalidCredentialsException;
import com.nursena.payflow.user.domain.exception.UserAccountUnavailableException;
import com.nursena.payflow.user.domain.model.EmailAddress;
import com.nursena.payflow.user.domain.model.RefreshTokenDigest;
import com.nursena.payflow.user.domain.model.RefreshTokenFamily;
import com.nursena.payflow.user.domain.model.RefreshTokenRecord;
import com.nursena.payflow.user.domain.model.User;
import com.nursena.payflow.user.domain.model.UserRole;
import com.nursena.payflow.user.domain.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class AuthenticateUserServiceTest {

    private static final UUID USER_ID =
        UUID.fromString(
            "8805681d-d537-42f2-8906-5da1f0666ab7"
        );

    private static final Instant NOW =
        Instant.parse(
            "2026-07-28T12:00:00Z"
        );

    private static final Instant ACCESS_EXPIRES_AT =
        Instant.parse(
            "2026-07-28T12:15:00Z"
        );

    private static final Instant FAMILY_EXPIRES_AT =
        NOW.plus(
            Duration.ofDays(30)
        );

    private static final Instant REFRESH_EXPIRES_AT =
        NOW.plus(
            Duration.ofDays(7)
        );

    private static final String RAW_PASSWORD =
        "StrongPassword123!";

    private static final String PASSWORD_HASH =
        "hashed-password";

    private static final String ACCESS_TOKEN =
        "generated.jwt.token";

    private static final String REFRESH_TOKEN =
        "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA";

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private PasswordVerificationPort passwordVerification;

    @Mock
    private RefreshTokenGenerationPort refreshTokenGeneration;

    @Mock
    private RefreshTokenDigestPort refreshTokenDigest;

    @Mock
    private RefreshTokenFamilyRepositoryPort familyRepository;

    @Mock
    private RefreshTokenRecordRepositoryPort recordRepository;

    @Mock
    private AccessTokenGenerationPort accessTokenGeneration;

    @Mock
    private LoginRateLimitPort loginRateLimit;

    private RefreshSessionLifetimePolicy lifetimePolicy;
    private Clock clock;
    private AuthenticateUserService authenticateUserService;

    @BeforeEach
    void setUp() {
        lifetimePolicy =
            new RefreshSessionLifetimePolicy(
                Duration.ofDays(7),
                Duration.ofDays(30)
            );

        clock =
            Clock.fixed(
                NOW,
                ZoneOffset.UTC
            );

        lenient()
            .when(
                loginRateLimit.evaluate(
                    any(LoginRateLimitRequest.class)
                )
            )
            .thenReturn(
                LoginRateLimitDecision.allowed()
            );

        authenticateUserService =
            new AuthenticateUserService(
                userRepository,
                passwordVerification,
                refreshTokenGeneration,
                refreshTokenDigest,
                familyRepository,
                recordRepository,
                accessTokenGeneration,
                loginRateLimit,
                lifetimePolicy,
                clock
            );
    }

    @Test
    void shouldRejectUnverifiedUserOnlyAfterPasswordMatches() {
        EmailAddress email =
            EmailAddress.of(
                "unverified@example.com"
            );

        User user =
            unverifiedActiveUser(email);

        when(userRepository.findByEmail(email))
            .thenReturn(Optional.of(user));

        when(passwordVerification.matches(
            RAW_PASSWORD,
            PASSWORD_HASH
        ))
            .thenReturn(true);

        assertThatThrownBy(() ->
            authenticateUserService.authenticate(
                new AuthenticateUserCommand(
                    email.value(),
                    RAW_PASSWORD,
                    "203.0.113.10"
                )
            )
        )
            .isInstanceOf(
                UserAccountUnavailableException.class
            );

        verify(passwordVerification).matches(
            RAW_PASSWORD,
            PASSWORD_HASH
        );
        verifyNoInteractions(
            refreshTokenGeneration,
            refreshTokenDigest,
            familyRepository,
            recordRepository,
            accessTokenGeneration
        );
    }

    @Test
    void shouldAuthenticateAndIssueInitialRefreshSession() {
        EmailAddress email =
            EmailAddress.of(
                "nursena@example.com"
            );

        User user =
            activeUser(email);

        RefreshTokenDigest digest =
            digest();

        when(userRepository.findByEmail(email))
            .thenReturn(
                Optional.of(user)
            );

        when(passwordVerification.matches(
            RAW_PASSWORD,
            PASSWORD_HASH
        ))
            .thenReturn(true);

        when(refreshTokenGeneration.generate())
            .thenReturn(
                new GeneratedRefreshToken(
                    REFRESH_TOKEN
                )
            );

        when(refreshTokenDigest.digest(
            REFRESH_TOKEN
        ))
            .thenReturn(digest);

        when(familyRepository.save(
            any(RefreshTokenFamily.class)
        ))
            .thenAnswer(
                invocation ->
                    invocation.getArgument(0)
            );

        when(recordRepository.save(
            any(RefreshTokenRecord.class)
        ))
            .thenAnswer(
                invocation ->
                    invocation.getArgument(0)
            );

        when(accessTokenGeneration.generate(user))
            .thenReturn(
                new GeneratedAccessToken(
                    ACCESS_TOKEN,
                    ACCESS_EXPIRES_AT
                )
            );

        AuthenticateUserResult result =
            authenticateUserService.authenticate(
                new AuthenticateUserCommand(
                    "  NURSENA@EXAMPLE.COM  ",
                    RAW_PASSWORD,
                    "203.0.113.10"
                )
            );

        assertThat(result.accessToken())
            .isEqualTo(ACCESS_TOKEN);

        assertThat(result.expiresAt())
            .isEqualTo(
                ACCESS_EXPIRES_AT
            );

        assertThat(result.refreshToken())
            .isEqualTo(
                REFRESH_TOKEN
            );

        assertThat(
            result.refreshTokenExpiresAt()
        )
            .isEqualTo(
                REFRESH_EXPIRES_AT
            );

        ArgumentCaptor<RefreshTokenFamily>
            familyCaptor =
            ArgumentCaptor.forClass(
                RefreshTokenFamily.class
            );

        ArgumentCaptor<RefreshTokenRecord>
            recordCaptor =
            ArgumentCaptor.forClass(
                RefreshTokenRecord.class
            );

        InOrder issuanceOrder =
            inOrder(
                refreshTokenGeneration,
                refreshTokenDigest,
                familyRepository,
                recordRepository,
                accessTokenGeneration
            );

        issuanceOrder
            .verify(refreshTokenGeneration)
            .generate();

        issuanceOrder
            .verify(refreshTokenDigest)
            .digest(REFRESH_TOKEN);

        issuanceOrder
            .verify(familyRepository)
            .save(
                familyCaptor.capture()
            );

        issuanceOrder
            .verify(recordRepository)
            .save(
                recordCaptor.capture()
            );

        issuanceOrder
            .verify(accessTokenGeneration)
            .generate(user);

        RefreshTokenFamily savedFamily =
            familyCaptor.getValue();

        RefreshTokenRecord savedRecord =
            recordCaptor.getValue();

        assertThat(savedFamily.id())
            .isNotNull();

        assertThat(savedFamily.userId())
            .isEqualTo(USER_ID);

        assertThat(savedFamily.createdAt())
            .isEqualTo(NOW);

        assertThat(savedFamily.expiresAt())
            .isEqualTo(
                FAMILY_EXPIRES_AT
            );

        assertThat(savedFamily.isRevoked())
            .isFalse();

        assertThat(savedRecord.id())
            .isNotNull();

        assertThat(savedRecord.familyId())
            .isEqualTo(
                savedFamily.id()
            );

        assertThat(savedRecord.digest())
            .isEqualTo(digest);

        assertThat(savedRecord.issuedAt())
            .isEqualTo(NOW);

        assertThat(savedRecord.expiresAt())
            .isEqualTo(
                REFRESH_EXPIRES_AT
            );

        assertThat(
            savedRecord.isActiveAt(
                savedFamily,
                NOW
            )
        )
            .isTrue();

        verify(userRepository)
            .findByEmail(email);

        verify(passwordVerification)
            .matches(
                RAW_PASSWORD,
                PASSWORD_HASH
            );

        verify(loginRateLimit)
            .evaluate(
                argThat(request ->
                    request.identity().equals(email)
                        && request.clientAddress()
                            .equals("203.0.113.10")
                )
            );

        verify(loginRateLimit)
            .resetIdentity(email);
    }

    @Test
    void shouldDeclareWriteTransactionOnAuthentication()
        throws NoSuchMethodException {

        Method authenticate =
            AuthenticateUserService.class
                .getDeclaredMethod(
                    "authenticate",
                    AuthenticateUserCommand.class
                );

        Transactional transactional =
            authenticate.getAnnotation(
                Transactional.class
            );

        assertThat(transactional)
            .isNotNull();

        assertThat(transactional.readOnly())
            .isFalse();

        assertThat(
            AuthenticateUserService.class
                .getAnnotation(
                    Transactional.class
                )
        )
            .isNull();
    }

    @Test
    void shouldRejectUnknownEmailWithoutIssuingCredentials() {
        EmailAddress email =
            EmailAddress.of(
                "unknown@example.com"
            );

        when(userRepository.findByEmail(email))
            .thenReturn(
                Optional.empty()
            );

        assertThatThrownBy(() ->
            authenticateUserService.authenticate(
                new AuthenticateUserCommand(
                    "unknown@example.com",
                    RAW_PASSWORD,
                    "203.0.113.10"
                )
            )
        )
            .isInstanceOf(
                InvalidCredentialsException.class
            )
            .hasMessage(
                "Email or password is incorrect."
            );

        verify(userRepository)
            .findByEmail(email);

        verifyNoInteractions(
            passwordVerification,
            refreshTokenGeneration,
            refreshTokenDigest,
            familyRepository,
            recordRepository,
            accessTokenGeneration
        );
    }

    @Test
    void shouldRejectIncorrectPasswordWithoutIssuingCredentials() {
        EmailAddress email =
            EmailAddress.of(
                "nursena@example.com"
            );

        User user =
            activeUser(email);

        when(userRepository.findByEmail(email))
            .thenReturn(
                Optional.of(user)
            );

        when(passwordVerification.matches(
            "IncorrectPassword123!",
            PASSWORD_HASH
        ))
            .thenReturn(false);

        assertThatThrownBy(() ->
            authenticateUserService.authenticate(
                new AuthenticateUserCommand(
                    "nursena@example.com",
                    "IncorrectPassword123!",
                    "203.0.113.10"
                )
            )
        )
            .isInstanceOf(
                InvalidCredentialsException.class
            );

        verifyNoInteractions(
            refreshTokenGeneration,
            refreshTokenDigest,
            familyRepository,
            recordRepository,
            accessTokenGeneration
        );
    }

    @Test
    void shouldRejectUnavailableAccountWithoutIssuingCredentials() {
        EmailAddress email =
            EmailAddress.of(
                "nursena@example.com"
            );

        User user =
            unavailableUser(email);

        when(userRepository.findByEmail(email))
            .thenReturn(
                Optional.of(user)
            );

        when(passwordVerification.matches(
            RAW_PASSWORD,
            PASSWORD_HASH
        ))
            .thenReturn(true);

        assertThatThrownBy(() ->
            authenticateUserService.authenticate(
                new AuthenticateUserCommand(
                    "nursena@example.com",
                    RAW_PASSWORD,
                    "203.0.113.10"
                )
            )
        )
            .isInstanceOf(
                UserAccountUnavailableException.class
            );

        verifyNoInteractions(
            refreshTokenGeneration,
            refreshTokenDigest,
            familyRepository,
            recordRepository,
            accessTokenGeneration
        );
    }

    @Test
    void shouldStopWhenFamilyPersistenceFails() {
        User user =
            activeUser(
                EmailAddress.of(
                    "nursena@example.com"
                )
            );

        stubValidAuthentication(user);

        when(familyRepository.save(
            any(RefreshTokenFamily.class)
        ))
            .thenThrow(
                new IllegalStateException(
                    "family persistence failed"
                )
            );

        assertThatThrownBy(() ->
            authenticateUserService.authenticate(
                command()
            )
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessage(
                "family persistence failed"
            );

        verify(recordRepository, never())
            .save(
                any(RefreshTokenRecord.class)
            );

        verifyNoInteractions(
            accessTokenGeneration
        );
    }

    @Test
    void shouldStopWhenRecordPersistenceFails() {
        User user =
            activeUser(
                EmailAddress.of(
                    "nursena@example.com"
                )
            );

        stubValidAuthentication(user);

        when(familyRepository.save(
            any(RefreshTokenFamily.class)
        ))
            .thenAnswer(
                invocation ->
                    invocation.getArgument(0)
            );

        when(recordRepository.save(
            any(RefreshTokenRecord.class)
        ))
            .thenThrow(
                new IllegalStateException(
                    "record persistence failed"
                )
            );

        assertThatThrownBy(() ->
            authenticateUserService.authenticate(
                command()
            )
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessage(
                "record persistence failed"
            );

        verifyNoInteractions(
            accessTokenGeneration
        );
    }

    @Test
    void shouldGenerateAccessTokenAfterSessionPersistence() {
        User user =
            activeUser(
                EmailAddress.of(
                    "nursena@example.com"
                )
            );

        stubValidAuthentication(user);

        when(familyRepository.save(
            any(RefreshTokenFamily.class)
        ))
            .thenAnswer(
                invocation ->
                    invocation.getArgument(0)
            );

        when(recordRepository.save(
            any(RefreshTokenRecord.class)
        ))
            .thenAnswer(
                invocation ->
                    invocation.getArgument(0)
            );

        when(accessTokenGeneration.generate(user))
            .thenThrow(
                new IllegalStateException(
                    "access token generation failed"
                )
            );

        assertThatThrownBy(() ->
            authenticateUserService.authenticate(
                command()
            )
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessage(
                "access token generation failed"
            );

        InOrder order =
            inOrder(
                familyRepository,
                recordRepository,
                accessTokenGeneration
            );

        order.verify(familyRepository)
            .save(
                any(RefreshTokenFamily.class)
            );

        order.verify(recordRepository)
            .save(
                any(RefreshTokenRecord.class)
            );

        order.verify(accessTokenGeneration)
            .generate(user);
    }

    @Test
    void shouldRejectBlockedAttemptBeforeUserLookup() {
        when(
            loginRateLimit.evaluate(
                any(LoginRateLimitRequest.class)
            )
        )
            .thenReturn(
                LoginRateLimitDecision.blocked(
                    LoginRateLimitDimension.BOTH,
                    Duration.ofSeconds(420)
                )
            );

        assertThatThrownBy(() ->
            authenticateUserService.authenticate(
                command()
            )
        )
            .isInstanceOf(
                LoginRateLimitExceededException.class
            )
            .hasMessage(
                "Too many login attempts. "
                    + "Try again later."
            )
            .satisfies(exception -> {
                LoginRateLimitExceededException
                    rateLimitException =
                    (LoginRateLimitExceededException)
                        exception;

                assertThat(
                    rateLimitException
                        .getBlockedDimension()
                )
                    .isEqualTo(
                        LoginRateLimitDimension.BOTH
                    );

                assertThat(
                    rateLimitException
                        .getRetryAfter()
                )
                    .isEqualTo(
                        Duration.ofSeconds(420)
                    );
            });

        verifyNoInteractions(
            userRepository,
            passwordVerification,
            refreshTokenGeneration,
            refreshTokenDigest,
            familyRepository,
            recordRepository,
            accessTokenGeneration
        );

        verify(loginRateLimit, never())
            .resetIdentity(
                any(EmailAddress.class)
            );
    }

    @Test
    void shouldFailClosedBeforeUserLookupWhenLimiterIsUnavailable() {
        LoginRateLimitUnavailableException failure =
            new LoginRateLimitUnavailableException(
                new IllegalStateException(
                    "redis unavailable"
                )
            );

        when(
            loginRateLimit.evaluate(
                any(LoginRateLimitRequest.class)
            )
        )
            .thenThrow(failure);

        assertThatThrownBy(() ->
            authenticateUserService.authenticate(
                command()
            )
        )
            .isSameAs(failure);

        verifyNoInteractions(
            userRepository,
            passwordVerification,
            refreshTokenGeneration,
            refreshTokenDigest,
            familyRepository,
            recordRepository,
            accessTokenGeneration
        );

        verify(loginRateLimit, never())
            .resetIdentity(
                any(EmailAddress.class)
            );
    }

    @Test
    void shouldRetainIdentityCounterAfterInvalidCredentials() {
        EmailAddress email =
            EmailAddress.of(
                "nursena@example.com"
            );

        when(userRepository.findByEmail(email))
            .thenReturn(
                Optional.empty()
            );

        assertThatThrownBy(() ->
            authenticateUserService.authenticate(
                command()
            )
        )
            .isInstanceOf(
                InvalidCredentialsException.class
            );

        verify(loginRateLimit, never())
            .resetIdentity(email);
    }

    @Test
    void shouldPropagateIdentityResetFailureAfterCredentialIssuance() {
        User user =
            activeUser(
                EmailAddress.of(
                    "nursena@example.com"
                )
            );

        stubValidAuthentication(user);

        when(familyRepository.save(
            any(RefreshTokenFamily.class)
        ))
            .thenAnswer(
                invocation ->
                    invocation.getArgument(0)
            );

        when(recordRepository.save(
            any(RefreshTokenRecord.class)
        ))
            .thenAnswer(
                invocation ->
                    invocation.getArgument(0)
            );

        when(accessTokenGeneration.generate(user))
            .thenReturn(
                new GeneratedAccessToken(
                    ACCESS_TOKEN,
                    ACCESS_EXPIRES_AT
                )
            );

        LoginRateLimitUnavailableException failure =
            new LoginRateLimitUnavailableException(
                new IllegalStateException(
                    "redis reset failed"
                )
            );

        org.mockito.Mockito.doThrow(failure)
            .when(loginRateLimit)
            .resetIdentity(
                user.email()
            );

        assertThatThrownBy(() ->
            authenticateUserService.authenticate(
                command()
            )
        )
            .isSameAs(failure);

        verify(accessTokenGeneration)
            .generate(user);

        verify(loginRateLimit)
            .resetIdentity(
                user.email()
            );
    }

    private void stubValidAuthentication(
        User user
    ) {
        when(userRepository.findByEmail(
            user.email()
        ))
            .thenReturn(
                Optional.of(user)
            );

        when(passwordVerification.matches(
            RAW_PASSWORD,
            PASSWORD_HASH
        ))
            .thenReturn(true);

        when(refreshTokenGeneration.generate())
            .thenReturn(
                new GeneratedRefreshToken(
                    REFRESH_TOKEN
                )
            );

        when(refreshTokenDigest.digest(
            REFRESH_TOKEN
        ))
            .thenReturn(
                digest()
            );
    }

    private static AuthenticateUserCommand command() {
        return new AuthenticateUserCommand(
            "nursena@example.com",
            RAW_PASSWORD,
            "203.0.113.10"
        );
    }

    private static RefreshTokenDigest digest() {
        byte[] bytes =
            new byte[
                RefreshTokenDigest
                    .SHA_256_LENGTH_BYTES
            ];

        Arrays.fill(
            bytes,
            (byte) 7
        );

        return RefreshTokenDigest.of(
            bytes
        );
    }

    private static User unverifiedActiveUser(
        EmailAddress email
    ) {
        return User.rehydrate(
            USER_ID,
            email,
            PASSWORD_HASH,
            UserRole.USER,
            UserStatus.ACTIVE,
            null,
            NOW.minusSeconds(60),
            NOW.minusSeconds(60)
        );
    }

    private static User activeUser(
        EmailAddress email
    ) {
        return User.rehydrate(
            USER_ID,
            email,
            PASSWORD_HASH,
            UserRole.USER,
            UserStatus.ACTIVE,
            NOW,
            NOW,
            NOW
        );
    }

    private static User unavailableUser(
        EmailAddress email
    ) {
        return User.rehydrate(
            USER_ID,
            email,
            PASSWORD_HASH,
            UserRole.USER,
            UserStatus.SUSPENDED,
            NOW,
            NOW,
            NOW
        );
    }
}

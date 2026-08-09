package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

import com.nursena.payflow.user.application.exception.LoginRateLimitExceededException;
import com.nursena.payflow.user.application.exception.LoginRateLimitUnavailableException;
import com.nursena.payflow.user.application.port.in.AuthenticateUserCommand;
import com.nursena.payflow.user.application.port.in.AuthenticateUserResult;
import com.nursena.payflow.user.application.port.in.AuthenticatedUserResult;
import com.nursena.payflow.user.application.port.in.MfaChallengeRequiredResult;
import com.nursena.payflow.user.application.port.out.LoginRateLimitDecision;
import com.nursena.payflow.user.application.port.out.LoginRateLimitDimension;
import com.nursena.payflow.user.application.port.out.LoginRateLimitPort;
import com.nursena.payflow.user.application.port.out.LoginRateLimitRequest;
import com.nursena.payflow.user.application.port.out.MfaAuthenticatorRepositoryPort;
import com.nursena.payflow.user.application.port.out.PasswordVerificationPort;
import com.nursena.payflow.user.application.port.out.UserRepositoryPort;
import com.nursena.payflow.user.domain.exception.InvalidCredentialsException;
import com.nursena.payflow.user.domain.exception.UserAccountUnavailableException;
import com.nursena.payflow.user.domain.model.EmailAddress;
import com.nursena.payflow.user.domain.model.MfaAuthenticator;
import com.nursena.payflow.user.domain.model.MfaLifecycleState;
import com.nursena.payflow.user.domain.model.ProtectedMfaSecret;
import com.nursena.payflow.user.domain.model.User;
import com.nursena.payflow.user.domain.model.UserRole;
import com.nursena.payflow.user.domain.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class AuthenticateUserServiceTest {

    private static final UUID USER_ID = UUID.fromString("8805681d-d537-42f2-8906-5da1f0666ab7");
    private static final Instant NOW = Instant.parse("2026-08-08T12:00:00Z");
    private static final String PASSWORD = "StrongPassword123!";
    private static final String HASH = "hashed-password";
    private static final EmailAddress EMAIL = EmailAddress.of("nursena@example.com");

    @Mock UserRepositoryPort userRepository;
    @Mock PasswordVerificationPort passwordVerification;
    @Mock MfaAuthenticatorRepositoryPort authenticatorRepository;
    @Mock AuthenticationCredentialIssuer credentialIssuer;
    @Mock MfaLoginChallengeIssuer challengeIssuer;
    @Mock LoginRateLimitPort loginRateLimit;

    private AuthenticateUserService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new AuthenticateUserService(
            userRepository,
            passwordVerification,
            authenticatorRepository,
            credentialIssuer,
            challengeIssuer,
            loginRateLimit,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        user = activeUser(HASH);
    }

    @Test
    void shouldIssueCredentialsWhenMfaIsDisabled() {
        allowLogin();
        stubPasswordSuccess(user);
        AuthenticatedUserResult credentials = credentials();
        when(authenticatorRepository.findByUserIdForUpdate(USER_ID))
            .thenReturn(Optional.empty());
        when(credentialIssuer.issue(user, NOW)).thenReturn(credentials);

        AuthenticateUserResult result = service.authenticate(command(PASSWORD));

        assertThat(result).isSameAs(credentials);
        verify(credentialIssuer).issue(user, NOW);
        verifyNoInteractions(challengeIssuer);
        verify(loginRateLimit).resetIdentity(EMAIL);
    }

    @Test
    void shouldReturnChallengeWithoutIssuingCredentialsWhenMfaIsEnabled() {
        allowLogin();
        stubPasswordSuccess(user);
        MfaAuthenticator authenticator = enabledAuthenticator();
        MfaChallengeRequiredResult challenge = new MfaChallengeRequiredResult(
            "opaque-challenge",
            NOW.plusSeconds(300)
        );
        when(authenticatorRepository.findByUserIdForUpdate(USER_ID))
            .thenReturn(Optional.of(authenticator));
        when(challengeIssuer.issue(USER_ID, NOW)).thenReturn(challenge);

        AuthenticateUserResult result = service.authenticate(command(PASSWORD));

        assertThat(result).isSameAs(challenge);
        verify(challengeIssuer).issue(USER_ID, NOW);
        verifyNoInteractions(credentialIssuer);
        verify(loginRateLimit).resetIdentity(EMAIL);
    }

    @Test
    void shouldTreatPendingEnrollmentAsMfaNotYetEnabled() {
        allowLogin();
        stubPasswordSuccess(user);
        when(authenticatorRepository.findByUserIdForUpdate(USER_ID))
            .thenReturn(Optional.of(pendingAuthenticator()));
        when(credentialIssuer.issue(user, NOW)).thenReturn(credentials());

        assertThat(service.authenticate(command(PASSWORD)))
            .isInstanceOf(AuthenticatedUserResult.class);
        verifyNoInteractions(challengeIssuer);
    }

    @Test
    void shouldRejectUnknownEmailBeforePasswordVerification() {
        allowLogin();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.authenticate(command(PASSWORD)))
            .isInstanceOf(InvalidCredentialsException.class);
        verifyNoInteractions(passwordVerification, credentialIssuer, challengeIssuer);
    }

    @Test
    void shouldRejectIncorrectPasswordWithoutLockingUser() {
        allowLogin();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordVerification.matches("WrongPassword123!", HASH)).thenReturn(false);
        assertThatThrownBy(() -> service.authenticate(command("WrongPassword123!")))
            .isInstanceOf(InvalidCredentialsException.class);
        verify(userRepository, never()).findByIdForUpdate(USER_ID);
        verifyNoInteractions(credentialIssuer, challengeIssuer);
    }

    @Test
    void shouldReverifyPasswordWhenHashChangedBeforeUserLock() {
        allowLogin();
        User changed = activeUser("new-hash");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordVerification.matches(PASSWORD, HASH)).thenReturn(true);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(changed));
        when(passwordVerification.matches(PASSWORD, "new-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.authenticate(command(PASSWORD)))
            .isInstanceOf(InvalidCredentialsException.class);
        verifyNoInteractions(credentialIssuer, challengeIssuer);
    }

    @Test
    void shouldRejectUnavailableAccountAfterLock() {
        allowLogin();
        User suspended = user(UserStatus.SUSPENDED, HASH, NOW);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordVerification.matches(PASSWORD, HASH)).thenReturn(true);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(suspended));

        assertThatThrownBy(() -> service.authenticate(command(PASSWORD)))
            .isInstanceOf(UserAccountUnavailableException.class);
        verifyNoInteractions(credentialIssuer, challengeIssuer);
    }

    @Test
    void shouldRejectUnverifiedAccountAfterPasswordMatches() {
        allowLogin();
        User unverified = user(UserStatus.ACTIVE, HASH, null);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unverified));
        when(passwordVerification.matches(PASSWORD, HASH)).thenReturn(true);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(unverified));

        assertThatThrownBy(() -> service.authenticate(command(PASSWORD)))
            .isInstanceOf(UserAccountUnavailableException.class);
    }

    @Test
    void shouldEnforceRateLimitBeforeUserLookup() {
        when(loginRateLimit.evaluate(any(LoginRateLimitRequest.class)))
            .thenReturn(LoginRateLimitDecision.blocked(
                LoginRateLimitDimension.IDENTITY,
                Duration.ofSeconds(60)
            ));
        assertThatThrownBy(() -> service.authenticate(command(PASSWORD)))
            .isInstanceOf(LoginRateLimitExceededException.class);
        verifyNoInteractions(userRepository, credentialIssuer, challengeIssuer);
    }

    @Test
    void shouldFailClosedWhenLoginLimiterIsUnavailable() {
        LoginRateLimitUnavailableException failure =
            new LoginRateLimitUnavailableException(new IllegalStateException("redis"));
        when(loginRateLimit.evaluate(any(LoginRateLimitRequest.class)))
            .thenThrow(failure);
        assertThatThrownBy(() -> service.authenticate(command(PASSWORD)))
            .isSameAs(failure);
        verifyNoInteractions(userRepository, credentialIssuer, challengeIssuer);
    }

    @Test
    void shouldNotResetIdentityAfterInvalidCredentials() {
        allowLogin();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.authenticate(command(PASSWORD)))
            .isInstanceOf(InvalidCredentialsException.class);
        verify(loginRateLimit, never()).resetIdentity(any(EmailAddress.class));
    }

    @Test
    void shouldResetPasswordLimiterAfterChallengeCreation() {
        allowLogin();
        stubPasswordSuccess(user);
        when(authenticatorRepository.findByUserIdForUpdate(USER_ID))
            .thenReturn(Optional.of(enabledAuthenticator()));
        when(challengeIssuer.issue(USER_ID, NOW)).thenReturn(
            new MfaChallengeRequiredResult("challenge", NOW.plusSeconds(300))
        );
        service.authenticate(command(PASSWORD));
        verify(loginRateLimit).resetIdentity(EMAIL);
    }

    @Test
    void shouldDeclareWriteTransactionOnAuthentication() throws Exception {
        Method method = AuthenticateUserService.class.getDeclaredMethod(
            "authenticate",
            AuthenticateUserCommand.class
        );
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    private void allowLogin() {
        when(loginRateLimit.evaluate(any(LoginRateLimitRequest.class)))
            .thenReturn(LoginRateLimitDecision.allowed());
    }

    private void stubPasswordSuccess(User lockedUser) {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordVerification.matches(PASSWORD, HASH)).thenReturn(true);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(lockedUser));
    }

    private static AuthenticateUserCommand command(String password) {
        return new AuthenticateUserCommand(EMAIL.value(), password, "203.0.113.10");
    }

    private static AuthenticatedUserResult credentials() {
        return new AuthenticatedUserResult(
            "access",
            NOW.plusSeconds(900),
            "refresh",
            NOW.plusSeconds(3600)
        );
    }

    private static User activeUser(String hash) {
        return user(UserStatus.ACTIVE, hash, NOW);
    }

    private static User user(UserStatus status, String hash, Instant verifiedAt) {
        return User.rehydrate(
            USER_ID,
            EMAIL,
            hash,
            UserRole.USER,
            status,
            verifiedAt,
            NOW.minusSeconds(60),
            NOW.minusSeconds(60)
        );
    }

    private static MfaAuthenticator enabledAuthenticator() {
        return MfaAuthenticator.rehydrate(
            USER_ID,
            MfaLifecycleState.ENABLED,
            ProtectedMfaSecret.of(new byte[49]),
            null,
            NOW.minusSeconds(60),
            NOW.minusSeconds(120),
            NOW.minusSeconds(60)
        );
    }

    private static MfaAuthenticator pendingAuthenticator() {
        return MfaAuthenticator.beginEnrollment(
            USER_ID,
            ProtectedMfaSecret.of(new byte[49]),
            NOW.minusSeconds(60),
            NOW.plusSeconds(300)
        );
    }
}

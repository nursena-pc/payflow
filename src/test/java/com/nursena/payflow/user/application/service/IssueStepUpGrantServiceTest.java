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
import com.nursena.payflow.user.application.port.in.IssueStepUpGrantCommand;
import com.nursena.payflow.user.application.port.in.IssueStepUpGrantResult;
import com.nursena.payflow.user.application.port.out.MfaAuthenticatorRepositoryPort;
import com.nursena.payflow.user.application.port.out.UserRepositoryPort;
import com.nursena.payflow.user.domain.exception.InvalidStepUpGrantException;
import com.nursena.payflow.user.domain.exception.InvalidStepUpPurposeException;
import com.nursena.payflow.user.domain.exception.MfaStateConflictException;
import com.nursena.payflow.user.domain.exception.MfaVerificationFailedException;
import com.nursena.payflow.user.domain.exception.UserNotFoundException;
import com.nursena.payflow.user.domain.model.EmailAddress;
import com.nursena.payflow.user.domain.model.MfaAuthenticator;
import com.nursena.payflow.user.domain.model.MfaLifecycleState;
import com.nursena.payflow.user.domain.model.ProtectedMfaSecret;
import com.nursena.payflow.user.domain.model.StepUpPurpose;
import com.nursena.payflow.user.domain.model.User;
import com.nursena.payflow.user.domain.model.UserRole;
import com.nursena.payflow.user.domain.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IssueStepUpGrantServiceTest {

    private static final UUID USER_ID =
        UUID.fromString("10000000-0000-0000-0000-000000000103");
    private static final Instant NOW =
        Instant.parse("2026-08-10T10:00:00.123456Z");

    @Mock AbuseProtectionEnforcementPort abuseProtection;
    @Mock UserRepositoryPort userRepository;
    @Mock MfaAuthenticatorRepositoryPort authenticatorRepository;
    @Mock MfaSecondFactorVerifier secondFactorVerifier;
    @Mock StepUpGrantIssuer grantIssuer;

    private IssueStepUpGrantService service;

    @BeforeEach
    void setUp() {
        lenient().when(abuseProtection.evaluate(any()))
            .thenReturn(AbuseProtectionDecision.allowed());
        service = new IssueStepUpGrantService(
            abuseProtection,
            userRepository,
            authenticatorRepository,
            secondFactorVerifier,
            grantIssuer,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldEnforceAbuseProtectionBeforeLoadingUser() {
        when(userRepository.findByIdForUpdate(USER_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issue(
            command("mfa-disable", "123456")
        )).isInstanceOf(UserNotFoundException.class);

        InOrder order = inOrder(abuseProtection, userRepository);
        order.verify(abuseProtection).evaluate(argThat(request ->
            request.workflow() == AbuseProtectionWorkflow.STEP_UP_GRANT_ISSUANCE
                && request.normalizedIdentity().equals(USER_ID.toString())
                && request.effectiveClientAddress().equals(
                    IpAddress.parse("203.0.113.10")
                )
        ));
        order.verify(userRepository).findByIdForUpdate(USER_ID);
    }

    @Test
    void shouldRejectBlockedStepUpBeforeSensitiveStateAccess() {
        when(abuseProtection.evaluate(any()))
            .thenReturn(AbuseProtectionDecision.blocked(
                AbuseProtectionDimension.CLIENT,
                Duration.ofSeconds(30)
            ));

        assertThatThrownBy(() -> service.issue(
            command("mfa-disable", "123456")
        )).isInstanceOf(InvalidStepUpGrantException.class);

        verifyNoInteractions(
            userRepository,
            authenticatorRepository,
            secondFactorVerifier,
            grantIssuer
        );
    }

    @Test
    void shouldFailClosedBeforeSensitiveStateAccessWhenAbuseProtectionIsUnavailable() {
        when(abuseProtection.evaluate(any()))
            .thenThrow(new AbuseProtectionUnavailableException(
                AbuseProtectionWorkflow.STEP_UP_GRANT_ISSUANCE,
                AbuseProtectionFailureMode.FAIL_CLOSED,
                new IllegalStateException("redis unavailable")
            ));

        assertThatThrownBy(() -> service.issue(
            command("mfa-disable", "123456")
        )).isInstanceOf(MfaSecurityUnavailableException.class);

        verifyNoInteractions(
            userRepository,
            authenticatorRepository,
            secondFactorVerifier,
            grantIssuer
        );
    }

    @Test
    void shouldIssueUserPurposeOnlyAfterEnabledSecondFactorProof() {
        User user = user(UserRole.USER, UserStatus.ACTIVE, true);
        MfaAuthenticator authenticator = enabledAuthenticator();
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(authenticatorRepository.findByUserIdForUpdate(USER_ID))
            .thenReturn(Optional.of(authenticator));
        when(secondFactorVerifier.verifyAndConsume(USER_ID, authenticator, "123456", NOW))
            .thenReturn(true);
        IssueStepUpGrantResult issued = new IssueStepUpGrantResult(
            "grant", "mfa-disable", NOW.plusSeconds(300)
        );
        when(grantIssuer.issue(USER_ID, StepUpPurpose.MFA_DISABLE, NOW))
            .thenReturn(issued);

        assertThat(service.issue(command("mfa-disable", "123456"))).isEqualTo(issued);
        verify(grantIssuer).issue(USER_ID, StepUpPurpose.MFA_DISABLE, NOW);
    }

    @Test
    void shouldRejectInvalidPurposeBeforeLoadingUser() {
        assertThatThrownBy(() -> service.issue(command("free-form", "123456")))
            .isInstanceOf(InvalidStepUpPurposeException.class);
        verifyNoInteractions(
            abuseProtection,
            userRepository,
            authenticatorRepository,
            secondFactorVerifier,
            grantIssuer
        );
    }

    @Test
    void shouldRequireEnabledAuthenticator() {
        when(userRepository.findByIdForUpdate(USER_ID))
            .thenReturn(Optional.of(user(UserRole.USER, UserStatus.ACTIVE, true)));
        when(authenticatorRepository.findByUserIdForUpdate(USER_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issue(command("mfa-disable", "123456")))
            .isInstanceOf(MfaStateConflictException.class);
        verifyNoInteractions(secondFactorVerifier, grantIssuer);
    }

    @Test
    void shouldRejectInvalidSecondFactorWithoutIssuingGrant() {
        User user = user(UserRole.USER, UserStatus.ACTIVE, true);
        MfaAuthenticator authenticator = enabledAuthenticator();
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(authenticatorRepository.findByUserIdForUpdate(USER_ID))
            .thenReturn(Optional.of(authenticator));
        when(secondFactorVerifier.verifyAndConsume(USER_ID, authenticator, "000000", NOW))
            .thenReturn(false);

        assertThatThrownBy(() -> service.issue(command("mfa-disable", "000000")))
            .isInstanceOf(MfaVerificationFailedException.class);
        verify(grantIssuer, never()).issue(any(), any(), any());
    }

    @Test
    void shouldRejectOperatorPurposeForNormalUserWithoutLeakingRolePolicy() {
        when(userRepository.findByIdForUpdate(USER_ID))
            .thenReturn(Optional.of(user(UserRole.USER, UserStatus.ACTIVE, true)));

        assertThatThrownBy(() -> service.issue(command("kafka-dead-letter-replay", "123456")))
            .isInstanceOf(InvalidStepUpGrantException.class);
        verifyNoInteractions(authenticatorRepository, secondFactorVerifier, grantIssuer);
    }

    @Test
    void shouldPermitOperatorPurposeForAdminWithEnabledMfa() {
        User admin = user(UserRole.ADMIN, UserStatus.ACTIVE, true);
        MfaAuthenticator authenticator = enabledAuthenticator();
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(admin));
        when(authenticatorRepository.findByUserIdForUpdate(USER_ID))
            .thenReturn(Optional.of(authenticator));
        when(secondFactorVerifier.verifyAndConsume(USER_ID, authenticator, "123456", NOW))
            .thenReturn(true);
        when(grantIssuer.issue(USER_ID, StepUpPurpose.KAFKA_DEAD_LETTER_DISCARD, NOW))
            .thenReturn(new IssueStepUpGrantResult(
                "grant", "kafka-dead-letter-discard", NOW.plusSeconds(300)
            ));

        assertThat(service.issue(command("kafka-dead-letter-discard", "123456")).purpose())
            .isEqualTo("kafka-dead-letter-discard");
    }

    private static IssueStepUpGrantCommand command(String purpose, String code) {
        return new IssueStepUpGrantCommand(
            USER_ID,
            purpose,
            code,
            IpAddress.parse("203.0.113.10")
        );
    }

    private static User user(UserRole role, UserStatus status, boolean verified) {
        Instant created = NOW.minusSeconds(600);
        return User.rehydrate(
            USER_ID,
            EmailAddress.of("stepup@example.com"),
            "hash",
            role,
            status,
            verified ? created.plusSeconds(1) : null,
            created,
            created.plusSeconds(2)
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
}

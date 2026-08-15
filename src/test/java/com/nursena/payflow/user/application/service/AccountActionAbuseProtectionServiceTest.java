package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;

import com.nursena.payflow.abuseprotection.application.exception.AbuseProtectionUnavailableException;
import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionFailureMode;
import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionWorkflow;
import com.nursena.payflow.abuseprotection.application.port.out.AbuseProtectionDecision;
import com.nursena.payflow.abuseprotection.application.port.out.AbuseProtectionDimension;
import com.nursena.payflow.abuseprotection.application.port.out.AbuseProtectionEnforcementPort;
import com.nursena.payflow.abuseprotection.application.port.out.AbuseProtectionRequest;
import com.nursena.payflow.clientcontext.domain.IpAddress;
import com.nursena.payflow.user.application.port.in.RequestEmailVerificationCommand;
import com.nursena.payflow.user.application.port.in.RequestPasswordRecoveryCommand;
import com.nursena.payflow.user.application.port.out.UserRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountActionAbuseProtectionServiceTest {

    private static final IpAddress CLIENT =
        IpAddress.parse("203.0.113.10");

    @Mock
    private AbuseProtectionEnforcementPort abuseProtection;

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private EmailVerificationPreparationService emailPreparation;

    @Mock
    private PasswordRecoveryPreparationService recoveryPreparation;

    @Test
    void shouldNormalizeIdentityAndEvaluateBeforeEmailLookup() {
        when(abuseProtection.evaluate(any()))
            .thenReturn(AbuseProtectionDecision.allowed());

        RequestEmailVerificationService service =
            new RequestEmailVerificationService(
                abuseProtection,
                userRepository,
                emailPreparation
            );

        service.request(
            new RequestEmailVerificationCommand(
                "  Nursena@Example.COM  ",
                CLIENT
            )
        );

        ArgumentCaptor<AbuseProtectionRequest> request =
            ArgumentCaptor.forClass(
                AbuseProtectionRequest.class
            );
        InOrder order = inOrder(
            abuseProtection,
            userRepository
        );
        order.verify(abuseProtection).evaluate(request.capture());
        order.verify(userRepository).findByEmailForUpdate(any());

        assertThat(request.getValue().workflow()).isEqualTo(
            AbuseProtectionWorkflow.EMAIL_VERIFICATION_REQUEST
        );
        assertThat(request.getValue().normalizedIdentity())
            .isEqualTo("nursena@example.com");
        assertThat(request.getValue().effectiveClientAddress())
            .isEqualTo(CLIENT);
    }

    @Test
    void shouldSuppressEmailSideEffectsWhenBlocked() {
        when(abuseProtection.evaluate(any())).thenReturn(
            AbuseProtectionDecision.blocked(
                AbuseProtectionDimension.BOTH,
                Duration.ofSeconds(30)
            )
        );

        new RequestEmailVerificationService(
            abuseProtection,
            userRepository,
            emailPreparation
        ).request(
            new RequestEmailVerificationCommand(
                "nursena@example.com",
                CLIENT
            )
        );

        verifyNoInteractions(userRepository, emailPreparation);
    }

    @Test
    void shouldSuppressRecoverySideEffectsWhenBlocked() {
        when(abuseProtection.evaluate(any())).thenReturn(
            AbuseProtectionDecision.blocked(
                AbuseProtectionDimension.IDENTITY,
                Duration.ofSeconds(30)
            )
        );

        new RequestPasswordRecoveryService(
            abuseProtection,
            userRepository,
            recoveryPreparation
        ).request(
            new RequestPasswordRecoveryCommand(
                "nursena@example.com",
                CLIENT
            )
        );

        verifyNoInteractions(userRepository, recoveryPreparation);
    }

    @Test
    void shouldFailClosedWithoutLookupOrSideEffect() {
        when(abuseProtection.evaluate(any())).thenThrow(
            new AbuseProtectionUnavailableException(
                AbuseProtectionWorkflow
                    .PASSWORD_RECOVERY_REQUEST,
                AbuseProtectionFailureMode.FAIL_CLOSED,
                new IllegalStateException("redis unavailable")
            )
        );

        new RequestPasswordRecoveryService(
            abuseProtection,
            userRepository,
            recoveryPreparation
        ).request(
            new RequestPasswordRecoveryCommand(
                "nursena@example.com",
                CLIENT
            )
        );

        verify(userRepository, never())
            .findByEmailForUpdate(any());
        verifyNoInteractions(recoveryPreparation);
    }
}
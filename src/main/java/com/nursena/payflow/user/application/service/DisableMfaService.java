package com.nursena.payflow.user.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.user.application.port.in.DisableMfaCommand;
import com.nursena.payflow.user.application.port.in.DisableMfaUseCase;
import com.nursena.payflow.user.application.port.in.StepUpAuthorizationPolicy;
import com.nursena.payflow.user.application.port.out.AccountSecurityAuditPort;
import com.nursena.payflow.user.application.port.out.MfaAuthenticatorRepositoryPort;
import com.nursena.payflow.user.application.port.out.MfaRecoveryCodeRepositoryPort;
import com.nursena.payflow.user.application.port.out.RefreshTokenFamilyRepositoryPort;
import com.nursena.payflow.user.application.port.out.UserRepositoryPort;
import com.nursena.payflow.user.domain.exception.MfaStateConflictException;
import com.nursena.payflow.user.domain.exception.UserNotFoundException;
import com.nursena.payflow.user.domain.model.AccountSecurityAuditEvent;
import com.nursena.payflow.user.domain.model.MfaAuthenticator;
import com.nursena.payflow.user.domain.model.MfaLifecycleState;
import com.nursena.payflow.user.domain.model.RefreshTokenFamilyRevocationReason;
import com.nursena.payflow.user.domain.model.StepUpPurpose;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DisableMfaService implements DisableMfaUseCase {

    private final UserRepositoryPort userRepository;
    private final MfaAuthenticatorRepositoryPort authenticatorRepository;
    private final MfaRecoveryCodeRepositoryPort recoveryCodeRepository;
    private final StepUpAuthorizationPolicy stepUpAuthorizationPolicy;
    private final RefreshTokenFamilyRepositoryPort refreshTokenFamilyRepository;
    private final AccountSecurityAuditPort auditPort;
    private final Clock clock;

    public DisableMfaService(
        UserRepositoryPort userRepository,
        MfaAuthenticatorRepositoryPort authenticatorRepository,
        MfaRecoveryCodeRepositoryPort recoveryCodeRepository,
        StepUpAuthorizationPolicy stepUpAuthorizationPolicy,
        RefreshTokenFamilyRepositoryPort refreshTokenFamilyRepository,
        AccountSecurityAuditPort auditPort,
        Clock clock
    ) {
        this.userRepository =
            Objects.requireNonNull(userRepository);
        this.authenticatorRepository =
            Objects.requireNonNull(authenticatorRepository);
        this.recoveryCodeRepository =
            Objects.requireNonNull(recoveryCodeRepository);
        this.stepUpAuthorizationPolicy =
            Objects.requireNonNull(stepUpAuthorizationPolicy);
        this.refreshTokenFamilyRepository =
            Objects.requireNonNull(refreshTokenFamilyRepository);
        this.auditPort =
            Objects.requireNonNull(auditPort);
        this.clock =
            Objects.requireNonNull(clock);
    }

    @Override
    public void disable(DisableMfaCommand command) {
        Objects.requireNonNull(command, "command must not be null");

        UUID userId = command.userId();

        userRepository.findByIdForUpdate(userId)
            .orElseThrow(UserNotFoundException::new);

        MfaAuthenticator authenticator =
            authenticatorRepository.findByUserIdForUpdate(userId)
                .orElseThrow(MfaStateConflictException::new);

        if (authenticator.state() != MfaLifecycleState.ENABLED) {
            throw new MfaStateConflictException();
        }

        stepUpAuthorizationPolicy.requireAndConsume(
            userId,
            StepUpPurpose.MFA_DISABLE,
            command.stepUpGrant()
        );

        Instant occurredAt = clock.instant();

        recoveryCodeRepository.deleteAllByUserId(userId);
        authenticatorRepository.delete(authenticator);

        refreshTokenFamilyRepository.revokeAllActiveByUserId(
            userId,
            occurredAt,
            RefreshTokenFamilyRevocationReason.MFA_DISABLED
        );

        auditPort.append(
            AccountSecurityAuditEvent.mfaDisabled(
                UUID.randomUUID(),
                userId,
                occurredAt
            )
        );
    }
}

package com.nursena.payflow.user.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.user.application.port.in.RotateMfaRecoveryCodesCommand;
import com.nursena.payflow.user.application.port.in.RotateMfaRecoveryCodesResult;
import com.nursena.payflow.user.application.port.in.RotateMfaRecoveryCodesUseCase;
import com.nursena.payflow.user.application.port.in.StepUpAuthorizationPolicy;
import com.nursena.payflow.user.application.port.out.AccountSecurityAuditPort;
import com.nursena.payflow.user.application.port.out.MfaAuthenticatorRepositoryPort;
import com.nursena.payflow.user.application.port.out.MfaRecoveryCodeRepositoryPort;
import com.nursena.payflow.user.application.port.out.UserRepositoryPort;
import com.nursena.payflow.user.domain.exception.MfaStateConflictException;
import com.nursena.payflow.user.domain.exception.UserNotFoundException;
import com.nursena.payflow.user.domain.model.AccountSecurityAuditEvent;
import com.nursena.payflow.user.domain.model.MfaAuthenticator;
import com.nursena.payflow.user.domain.model.MfaLifecycleState;
import com.nursena.payflow.user.domain.model.StepUpPurpose;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RotateMfaRecoveryCodesService
    implements RotateMfaRecoveryCodesUseCase {

    private final UserRepositoryPort userRepository;
    private final MfaAuthenticatorRepositoryPort authenticatorRepository;
    private final MfaRecoveryCodeRepositoryPort recoveryCodeRepository;
    private final MfaRecoveryCodeIssuer recoveryCodeIssuer;
    private final StepUpAuthorizationPolicy stepUpAuthorizationPolicy;
    private final AccountSecurityAuditPort auditPort;
    private final Clock clock;

    public RotateMfaRecoveryCodesService(
        UserRepositoryPort userRepository,
        MfaAuthenticatorRepositoryPort authenticatorRepository,
        MfaRecoveryCodeRepositoryPort recoveryCodeRepository,
        MfaRecoveryCodeIssuer recoveryCodeIssuer,
        StepUpAuthorizationPolicy stepUpAuthorizationPolicy,
        AccountSecurityAuditPort auditPort,
        Clock clock
    ) {
        this.userRepository =
            Objects.requireNonNull(userRepository);
        this.authenticatorRepository =
            Objects.requireNonNull(authenticatorRepository);
        this.recoveryCodeRepository =
            Objects.requireNonNull(recoveryCodeRepository);
        this.recoveryCodeIssuer =
            Objects.requireNonNull(recoveryCodeIssuer);
        this.stepUpAuthorizationPolicy =
            Objects.requireNonNull(stepUpAuthorizationPolicy);
        this.auditPort =
            Objects.requireNonNull(auditPort);
        this.clock =
            Objects.requireNonNull(clock);
    }

    @Override
    public RotateMfaRecoveryCodesResult rotate(
        RotateMfaRecoveryCodesCommand command
    ) {
        Objects.requireNonNull(
            command,
            "command must not be null"
        );

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
            StepUpPurpose.RECOVERY_CODE_ROTATION,
            command.stepUpGrant()
        );

        Instant occurredAt = clock.instant();

        recoveryCodeRepository.deleteAllByUserId(userId);

        List<String> recoveryCodes =
            recoveryCodeIssuer.issue(userId, occurredAt);

        auditPort.append(
            AccountSecurityAuditEvent.recoveryCodesRotated(
                UUID.randomUUID(),
                userId,
                occurredAt
            )
        );

        return new RotateMfaRecoveryCodesResult(recoveryCodes);
    }
}
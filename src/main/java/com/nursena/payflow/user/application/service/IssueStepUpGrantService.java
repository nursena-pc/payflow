package com.nursena.payflow.user.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.nursena.payflow.abuseprotection.application.exception.AbuseProtectionUnavailableException;
import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionWorkflow;
import com.nursena.payflow.abuseprotection.application.port.out.AbuseProtectionDecision;
import com.nursena.payflow.abuseprotection.application.port.out.AbuseProtectionEnforcementPort;
import com.nursena.payflow.abuseprotection.application.port.out.AbuseProtectionRequest;
import com.nursena.payflow.user.application.exception.MfaSecurityUnavailableException;
import com.nursena.payflow.user.application.port.in.IssueStepUpGrantCommand;
import com.nursena.payflow.user.application.port.in.IssueStepUpGrantResult;
import com.nursena.payflow.user.application.port.in.IssueStepUpGrantUseCase;
import com.nursena.payflow.user.application.port.out.MfaAuthenticatorRepositoryPort;
import com.nursena.payflow.user.application.port.out.UserRepositoryPort;
import com.nursena.payflow.user.domain.exception.InvalidStepUpGrantException;
import com.nursena.payflow.user.domain.exception.InvalidStepUpPurposeException;
import com.nursena.payflow.user.domain.exception.MfaStateConflictException;
import com.nursena.payflow.user.domain.exception.MfaVerificationFailedException;
import com.nursena.payflow.user.domain.exception.UserAccountUnavailableException;
import com.nursena.payflow.user.domain.exception.UserNotFoundException;
import com.nursena.payflow.user.domain.model.MfaAuthenticator;
import com.nursena.payflow.user.domain.model.MfaLifecycleState;
import com.nursena.payflow.user.domain.model.StepUpPurpose;
import com.nursena.payflow.user.domain.model.User;
import com.nursena.payflow.user.domain.model.UserRole;
import com.nursena.payflow.user.domain.model.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IssueStepUpGrantService implements IssueStepUpGrantUseCase {

    private final AbuseProtectionEnforcementPort abuseProtection;
    private final UserRepositoryPort userRepository;
    private final MfaAuthenticatorRepositoryPort authenticatorRepository;
    private final MfaSecondFactorVerifier secondFactorVerifier;
    private final StepUpGrantIssuer grantIssuer;
    private final Clock clock;

    public IssueStepUpGrantService(
        AbuseProtectionEnforcementPort abuseProtection,
        UserRepositoryPort userRepository,
        MfaAuthenticatorRepositoryPort authenticatorRepository,
        MfaSecondFactorVerifier secondFactorVerifier,
        StepUpGrantIssuer grantIssuer,
        Clock clock
    ) {
        this.abuseProtection = abuseProtection;
        this.userRepository = userRepository;
        this.authenticatorRepository = authenticatorRepository;
        this.secondFactorVerifier = secondFactorVerifier;
        this.grantIssuer = grantIssuer;
        this.clock = clock;
    }

    @Override
    @Transactional
    public IssueStepUpGrantResult issue(IssueStepUpGrantCommand command) {
        IssueStepUpGrantCommand checked = Objects.requireNonNull(
            command,
            "command must not be null"
        );
        StepUpPurpose purpose = parsePurpose(checked.purpose());
        enforceAbuseProtection(checked);

        User user = userRepository.findByIdForUpdate(checked.subjectId())
            .orElseThrow(UserNotFoundException::new);

        if (user.status() != UserStatus.ACTIVE || !user.isEmailVerified()) {
            throw new UserAccountUnavailableException();
        }
        if (purpose.isOperatorPurpose() && user.role() != UserRole.ADMIN) {
            throw new InvalidStepUpGrantException();
        }

        MfaAuthenticator authenticator = authenticatorRepository
            .findByUserIdForUpdate(user.id())
            .filter(value -> value.state() == MfaLifecycleState.ENABLED)
            .orElseThrow(MfaStateConflictException::new);

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (!secondFactorVerifier.verifyAndConsume(
            user.id(),
            authenticator,
            checked.code(),
            now
        )) {
            throw new MfaVerificationFailedException();
        }

        return grantIssuer.issue(user.id(), purpose, now);
    }

    private void enforceAbuseProtection(
        IssueStepUpGrantCommand command
    ) {
        try {
            AbuseProtectionDecision decision =
                abuseProtection.evaluate(
                    new AbuseProtectionRequest(
                        AbuseProtectionWorkflow
                            .STEP_UP_GRANT_ISSUANCE,
                        command.subjectId().toString(),
                        command.effectiveClientAddress()
                    )
                );

            if (!decision.isAllowed()) {
                throw new InvalidStepUpGrantException();
            }
        }
        catch (AbuseProtectionUnavailableException exception) {
            throw new MfaSecurityUnavailableException();
        }
    }

    private static StepUpPurpose parsePurpose(String value) {
        try {
            return StepUpPurpose.fromValue(value);
        }
        catch (IllegalArgumentException exception) {
            throw new InvalidStepUpPurposeException();
        }
    }
}

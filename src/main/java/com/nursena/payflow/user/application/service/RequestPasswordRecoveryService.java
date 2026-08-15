package com.nursena.payflow.user.application.service;

import java.util.Objects;

import com.nursena.payflow.abuseprotection.application.exception.AbuseProtectionUnavailableException;
import com.nursena.payflow.abuseprotection.application.policy.AbuseProtectionWorkflow;
import com.nursena.payflow.abuseprotection.application.port.out.AbuseProtectionDecision;
import com.nursena.payflow.abuseprotection.application.port.out.AbuseProtectionEnforcementPort;
import com.nursena.payflow.abuseprotection.application.port.out.AbuseProtectionRequest;
import com.nursena.payflow.user.application.port.in.RequestPasswordRecoveryCommand;
import com.nursena.payflow.user.application.port.in.RequestPasswordRecoveryUseCase;
import com.nursena.payflow.user.application.port.out.UserRepositoryPort;
import com.nursena.payflow.user.domain.model.EmailAddress;
import com.nursena.payflow.user.domain.model.User;
import com.nursena.payflow.user.domain.model.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestPasswordRecoveryService
    implements RequestPasswordRecoveryUseCase {

    private final AbuseProtectionEnforcementPort abuseProtection;
    private final UserRepositoryPort userRepository;
    private final PasswordRecoveryPreparationService preparationService;

    public RequestPasswordRecoveryService(
        AbuseProtectionEnforcementPort abuseProtection,
        UserRepositoryPort userRepository,
        PasswordRecoveryPreparationService preparationService
    ) {
        this.abuseProtection = Objects.requireNonNull(
            abuseProtection,
            "abuseProtection must not be null"
        );
        this.userRepository = Objects.requireNonNull(
            userRepository,
            "userRepository must not be null"
        );
        this.preparationService = Objects.requireNonNull(
            preparationService,
            "preparationService must not be null"
        );
    }

    @Override
    @Transactional
    public void request(RequestPasswordRecoveryCommand command) {
        RequestPasswordRecoveryCommand checkedCommand =
            Objects.requireNonNull(
                command,
                "command must not be null"
            );

        EmailAddress email = EmailAddress.of(
            checkedCommand.email()
        );

        if (!isAllowed(checkedCommand, email)) {
            return;
        }

        User user = userRepository
            .findByEmailForUpdate(email)
            .orElse(null);

        if (!isEligible(user)) {
            return;
        }

        preparationService.prepare(user.id(), user.email());
    }

    private boolean isAllowed(
        RequestPasswordRecoveryCommand command,
        EmailAddress email
    ) {
        try {
            AbuseProtectionDecision decision =
                abuseProtection.evaluate(
                    new AbuseProtectionRequest(
                        AbuseProtectionWorkflow
                            .PASSWORD_RECOVERY_REQUEST,
                        email.value(),
                        command.effectiveClientAddress()
                    )
                );

            return decision.isAllowed();
        } catch (AbuseProtectionUnavailableException exception) {
            return false;
        }
    }

    private static boolean isEligible(User user) {
        return user != null
            && user.status() == UserStatus.ACTIVE
            && user.isEmailVerified();
    }
}
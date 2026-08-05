package com.nursena.payflow.user.application.service;

import java.util.Objects;

import com.nursena.payflow.user.application.port.in
    .RequestPasswordRecoveryCommand;
import com.nursena.payflow.user.application.port.in
    .RequestPasswordRecoveryUseCase;
import com.nursena.payflow.user.application.port.out
    .UserRepositoryPort;
import com.nursena.payflow.user.domain.model.EmailAddress;
import com.nursena.payflow.user.domain.model.User;
import com.nursena.payflow.user.domain.model.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation
    .Transactional;

@Service
public class RequestPasswordRecoveryService
    implements RequestPasswordRecoveryUseCase {

    private final UserRepositoryPort userRepository;
    private final PasswordRecoveryPreparationService
        preparationService;

    public RequestPasswordRecoveryService(
        UserRepositoryPort userRepository,
        PasswordRecoveryPreparationService
            preparationService
    ) {
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
    public void request(
        RequestPasswordRecoveryCommand command
    ) {
        RequestPasswordRecoveryCommand checkedCommand =
            Objects.requireNonNull(
                command,
                "command must not be null"
            );

        EmailAddress email = EmailAddress.of(
            checkedCommand.email()
        );

        User user = userRepository
            .findByEmailForUpdate(email)
            .orElse(null);

        if (!isEligible(user)) {
            return;
        }

        preparationService.prepare(
            user.id(),
            user.email()
        );
    }

    private static boolean isEligible(User user) {
        return user != null
            && user.status() == UserStatus.ACTIVE
            && user.isEmailVerified();
    }
}

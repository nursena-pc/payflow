package com.nursena.payflow.user.application.service;

import java.util.Objects;

import com.nursena.payflow.user.application.port.in
    .RequestEmailVerificationCommand;
import com.nursena.payflow.user.application.port.in
    .RequestEmailVerificationUseCase;
import com.nursena.payflow.user.application.port.out
    .UserRepositoryPort;
import com.nursena.payflow.user.domain.model.EmailAddress;
import com.nursena.payflow.user.domain.model.User;
import com.nursena.payflow.user.domain.model.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation
    .Transactional;

@Service
public class RequestEmailVerificationService
    implements RequestEmailVerificationUseCase {

    private final UserRepositoryPort userRepository;
    private final EmailVerificationPreparationService
        preparationService;

    public RequestEmailVerificationService(
        UserRepositoryPort userRepository,
        EmailVerificationPreparationService
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
        RequestEmailVerificationCommand command
    ) {
        RequestEmailVerificationCommand checkedCommand =
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
            && !user.isEmailVerified();
    }
}

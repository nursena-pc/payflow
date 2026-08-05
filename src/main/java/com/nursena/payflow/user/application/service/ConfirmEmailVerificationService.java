package com.nursena.payflow.user.application.service;

import static java.time.temporal.ChronoUnit.MICROS;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.user.application.port.in
    .ConfirmEmailVerificationCommand;
import com.nursena.payflow.user.application.port.in
    .ConfirmEmailVerificationUseCase;
import com.nursena.payflow.user.application.port.out
    .UserRepositoryPort;
import com.nursena.payflow.user.domain.exception
    .InvalidAccountActionCredentialException;
import com.nursena.payflow.user.domain.model
    .AccountActionCredentialPurpose;
import com.nursena.payflow.user.domain.model.User;
import com.nursena.payflow.user.domain.model.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation
    .Transactional;

@Service
public class ConfirmEmailVerificationService
    implements ConfirmEmailVerificationUseCase {

    private final AccountActionCredentialConsumer
        credentialConsumer;
    private final UserRepositoryPort userRepository;
    private final Clock clock;

    public ConfirmEmailVerificationService(
        AccountActionCredentialConsumer
            credentialConsumer,
        UserRepositoryPort userRepository,
        Clock clock
    ) {
        this.credentialConsumer = Objects.requireNonNull(
            credentialConsumer,
            "credentialConsumer must not be null"
        );
        this.userRepository = Objects.requireNonNull(
            userRepository,
            "userRepository must not be null"
        );
        this.clock = Objects.requireNonNull(
            clock,
            "clock must not be null"
        );
    }

    @Override
    @Transactional
    public void confirm(
        ConfirmEmailVerificationCommand command
    ) {
        ConfirmEmailVerificationCommand checkedCommand =
            Objects.requireNonNull(
                command,
                "command must not be null"
            );

        UUID userId = credentialConsumer.consume(
            checkedCommand.credential(),
            AccountActionCredentialPurpose
                .EMAIL_VERIFICATION
        );

        User user = userRepository
            .findById(userId)
            .orElseThrow(
                InvalidAccountActionCredentialException::new
            );

        if (user.status() != UserStatus.ACTIVE) {
            throw new
                InvalidAccountActionCredentialException();
        }

        Instant verifiedAt = clock.instant()
            .truncatedTo(MICROS);

        if (!user.verifyEmail(verifiedAt)) {
            throw new
                InvalidAccountActionCredentialException();
        }

        userRepository.save(user);
    }
}

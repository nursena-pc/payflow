package com.nursena.payflow.user.application.service;

import static java.time.temporal.ChronoUnit.MICROS;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.user.application.port.in
    .ConfirmPasswordRecoveryCommand;
import com.nursena.payflow.user.application.port.in
    .ConfirmPasswordRecoveryUseCase;
import com.nursena.payflow.user.application.port.out
    .PasswordHashingPort;
import com.nursena.payflow.user.application.port.out
    .RefreshTokenFamilyRepositoryPort;
import com.nursena.payflow.user.application.port.out
    .UserRepositoryPort;
import com.nursena.payflow.user.domain.exception
    .InvalidAccountActionCredentialException;
import com.nursena.payflow.user.domain.model
    .AccountActionCredentialPurpose;
import com.nursena.payflow.user.domain.model
    .PasswordRecovery;
import com.nursena.payflow.user.domain.model
    .RefreshTokenFamilyRevocationReason;
import com.nursena.payflow.user.domain.model.User;
import com.nursena.payflow.user.domain.model.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation
    .Transactional;

@Service
public class ConfirmPasswordRecoveryService
    implements ConfirmPasswordRecoveryUseCase {

    private final AccountActionCredentialConsumer
        credentialConsumer;
    private final PasswordHashingPort passwordHashing;
    private final UserRepositoryPort userRepository;
    private final RefreshTokenFamilyRepositoryPort
        familyRepository;
    private final Clock clock;

    public ConfirmPasswordRecoveryService(
        AccountActionCredentialConsumer
            credentialConsumer,
        PasswordHashingPort passwordHashing,
        UserRepositoryPort userRepository,
        RefreshTokenFamilyRepositoryPort
            familyRepository,
        Clock clock
    ) {
        this.credentialConsumer = Objects.requireNonNull(
            credentialConsumer,
            "credentialConsumer must not be null"
        );
        this.passwordHashing = Objects.requireNonNull(
            passwordHashing,
            "passwordHashing must not be null"
        );
        this.userRepository = Objects.requireNonNull(
            userRepository,
            "userRepository must not be null"
        );
        this.familyRepository = Objects.requireNonNull(
            familyRepository,
            "familyRepository must not be null"
        );
        this.clock = Objects.requireNonNull(
            clock,
            "clock must not be null"
        );
    }

    @Override
    @Transactional
    public void confirm(
        ConfirmPasswordRecoveryCommand command
    ) {
        ConfirmPasswordRecoveryCommand checkedCommand =
            Objects.requireNonNull(
                command,
                "command must not be null"
            );

        String replacementPasswordHash =
            passwordHashing.hash(
                checkedCommand.rawNewPassword()
            );

        UUID userId = credentialConsumer.consume(
            checkedCommand.credential(),
            AccountActionCredentialPurpose
                .PASSWORD_RECOVERY
        );

        User user = userRepository
            .findById(userId)
            .orElseThrow(
                InvalidAccountActionCredentialException::new
            );

        if (
            user.status() != UserStatus.ACTIVE
                || !user.isEmailVerified()
        ) {
            throw new
                InvalidAccountActionCredentialException();
        }

        Instant recoveredAt = clock.instant()
            .truncatedTo(MICROS);

        PasswordRecovery.replacePassword(
            user,
            replacementPasswordHash,
            recoveredAt
        );

        userRepository.save(user);

        familyRepository.revokeAllActiveByUserId(
            userId,
            recoveredAt,
            RefreshTokenFamilyRevocationReason
                .PASSWORD_RECOVERY
        );
    }
}

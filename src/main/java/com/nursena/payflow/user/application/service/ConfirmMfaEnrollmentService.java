package com.nursena.payflow.user.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import com.nursena.payflow.user.application.exception.MfaSecurityUnavailableException;
import com.nursena.payflow.user.application.port.in.ConfirmMfaEnrollmentCommand;
import com.nursena.payflow.user.application.port.in.ConfirmMfaEnrollmentResult;
import com.nursena.payflow.user.application.port.in.ConfirmMfaEnrollmentUseCase;
import com.nursena.payflow.user.application.port.out.MfaAuthenticatorRepositoryPort;
import com.nursena.payflow.user.application.port.out.MfaSecretProtectionFailureException;
import com.nursena.payflow.user.application.port.out.MfaSecretProtectionPort;
import com.nursena.payflow.user.application.port.out.TotpVerificationPort;
import com.nursena.payflow.user.application.port.out.UserRepositoryPort;
import com.nursena.payflow.user.domain.exception.MfaStateConflictException;
import com.nursena.payflow.user.domain.exception.MfaVerificationFailedException;
import com.nursena.payflow.user.domain.exception.UserAccountUnavailableException;
import com.nursena.payflow.user.domain.exception.UserNotFoundException;
import com.nursena.payflow.user.domain.model.MfaAuthenticator;
import com.nursena.payflow.user.domain.model.MfaLifecycleState;
import com.nursena.payflow.user.domain.model.User;
import com.nursena.payflow.user.domain.model.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ConfirmMfaEnrollmentService
    implements ConfirmMfaEnrollmentUseCase {

    private final UserRepositoryPort userRepository;
    private final MfaAuthenticatorRepositoryPort authenticatorRepository;
    private final MfaSecretProtectionPort secretProtection;
    private final TotpVerificationPort totpVerification;
    private final MfaRecoveryCodeIssuer recoveryCodeIssuer;
    private final Clock clock;

    public ConfirmMfaEnrollmentService(
        UserRepositoryPort userRepository,
        MfaAuthenticatorRepositoryPort authenticatorRepository,
        MfaSecretProtectionPort secretProtection,
        TotpVerificationPort totpVerification,
        MfaRecoveryCodeIssuer recoveryCodeIssuer,
        Clock clock
    ) {
        this.userRepository = userRepository;
        this.authenticatorRepository = authenticatorRepository;
        this.secretProtection = secretProtection;
        this.totpVerification = totpVerification;
        this.recoveryCodeIssuer = recoveryCodeIssuer;
        this.clock = clock;
    }

    @Override
    public ConfirmMfaEnrollmentResult confirm(
        ConfirmMfaEnrollmentCommand command
    ) {
        Objects.requireNonNull(command, "command must not be null");

        User user = userRepository
            .findByIdForUpdate(command.userId())
            .orElseThrow(UserNotFoundException::new);

        requireEligible(user);

        MfaAuthenticator authenticator = authenticatorRepository
            .findByUserIdForUpdate(user.id())
            .orElseThrow(MfaStateConflictException::new);

        Instant now = clock.instant()
            .truncatedTo(ChronoUnit.MICROS);

        if (
            authenticator.state() != MfaLifecycleState.PENDING
                || !authenticator.isEnrollmentActiveAt(now)
        ) {
            throw new MfaStateConflictException();
        }

        byte[] secret;
        try {
            secret = secretProtection.reveal(
                user.id(),
                authenticator.protectedSecret()
            );
        }
        catch (MfaSecretProtectionFailureException exception) {
            throw new MfaSecurityUnavailableException();
        }

        boolean verified;
        try {
            verified = totpVerification.verify(
                secret,
                command.code(),
                now
            );
        }
        finally {
            java.util.Arrays.fill(secret, (byte) 0);
        }

        if (!verified) {
            throw new MfaVerificationFailedException();
        }

        MfaAuthenticator activated =
            authenticatorRepository.save(
                authenticator.activate(now)
            );
        List<String> recoveryCodes = recoveryCodeIssuer.issue(
            user.id(),
            now
        );

        return new ConfirmMfaEnrollmentResult(
            activated.state(),
            activated.activatedAt(),
            recoveryCodes
        );
    }

    private static void requireEligible(User user) {
        if (
            user.status() != UserStatus.ACTIVE
                || !user.isEmailVerified()
        ) {
            throw new UserAccountUnavailableException();
        }
    }
}

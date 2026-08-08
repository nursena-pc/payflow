package com.nursena.payflow.user.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.nursena.payflow.user.application.exception.MfaSecurityUnavailableException;
import com.nursena.payflow.user.application.port.in.BeginMfaEnrollmentCommand;
import com.nursena.payflow.user.application.port.in.BeginMfaEnrollmentResult;
import com.nursena.payflow.user.application.port.in.BeginMfaEnrollmentUseCase;
import com.nursena.payflow.user.application.port.out.GeneratedTotpSecret;
import com.nursena.payflow.user.application.port.out.MfaAuthenticatorRepositoryPort;
import com.nursena.payflow.user.application.port.out.MfaSecretProtectionFailureException;
import com.nursena.payflow.user.application.port.out.MfaSecretProtectionPort;
import com.nursena.payflow.user.application.port.out.PasswordVerificationPort;
import com.nursena.payflow.user.application.port.out.TotpProvisioningUriPort;
import com.nursena.payflow.user.application.port.out.TotpSecretGenerationPort;
import com.nursena.payflow.user.application.port.out.UserRepositoryPort;
import com.nursena.payflow.user.domain.exception.MfaStateConflictException;
import com.nursena.payflow.user.domain.exception.MfaVerificationFailedException;
import com.nursena.payflow.user.domain.exception.UserAccountUnavailableException;
import com.nursena.payflow.user.domain.exception.UserNotFoundException;
import com.nursena.payflow.user.domain.model.MfaAuthenticator;
import com.nursena.payflow.user.domain.model.ProtectedMfaSecret;
import com.nursena.payflow.user.domain.model.User;
import com.nursena.payflow.user.domain.model.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BeginMfaEnrollmentService
    implements BeginMfaEnrollmentUseCase {

    private final UserRepositoryPort userRepository;
    private final MfaAuthenticatorRepositoryPort authenticatorRepository;
    private final PasswordVerificationPort passwordVerification;
    private final TotpSecretGenerationPort secretGeneration;
    private final MfaSecretProtectionPort secretProtection;
    private final TotpProvisioningUriPort provisioningUri;
    private final MfaEnrollmentLifetimePolicy lifetimePolicy;
    private final Clock clock;

    public BeginMfaEnrollmentService(
        UserRepositoryPort userRepository,
        MfaAuthenticatorRepositoryPort authenticatorRepository,
        PasswordVerificationPort passwordVerification,
        TotpSecretGenerationPort secretGeneration,
        MfaSecretProtectionPort secretProtection,
        TotpProvisioningUriPort provisioningUri,
        MfaEnrollmentLifetimePolicy lifetimePolicy,
        Clock clock
    ) {
        this.userRepository = userRepository;
        this.authenticatorRepository = authenticatorRepository;
        this.passwordVerification = passwordVerification;
        this.secretGeneration = secretGeneration;
        this.secretProtection = secretProtection;
        this.provisioningUri = provisioningUri;
        this.lifetimePolicy = lifetimePolicy;
        this.clock = clock;
    }

    @Override
    public BeginMfaEnrollmentResult begin(
        BeginMfaEnrollmentCommand command
    ) {
        Objects.requireNonNull(command, "command must not be null");

        User user = userRepository
            .findByIdForUpdate(command.userId())
            .orElseThrow(UserNotFoundException::new);

        requireEligible(user);

        if (!passwordVerification.matches(
            command.currentPassword(),
            user.passwordHash()
        )) {
            throw new MfaVerificationFailedException();
        }

        if (authenticatorRepository
            .findByUserIdForUpdate(user.id())
            .isPresent()) {
            throw new MfaStateConflictException();
        }

        Instant issuedAt = clock.instant()
            .truncatedTo(ChronoUnit.MICROS);
        GeneratedTotpSecret generated =
            secretGeneration.generate();

        ProtectedMfaSecret protectedSecret;
        byte[] plaintextSecret = generated.value();
        try {
            protectedSecret = secretProtection.protect(
                user.id(),
                plaintextSecret
            );
        }
        catch (MfaSecretProtectionFailureException exception) {
            throw new MfaSecurityUnavailableException();
        }
        finally {
            java.util.Arrays.fill(plaintextSecret, (byte) 0);
        }

        MfaAuthenticator saved = authenticatorRepository.save(
            MfaAuthenticator.beginEnrollment(
                user.id(),
                protectedSecret,
                issuedAt,
                lifetimePolicy.expiresAt(issuedAt)
            )
        );

        return new BeginMfaEnrollmentResult(
            saved.state(),
            generated.base32(),
            provisioningUri.build(
                user.email(),
                generated.base32()
            ),
            saved.enrollmentExpiresAt()
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

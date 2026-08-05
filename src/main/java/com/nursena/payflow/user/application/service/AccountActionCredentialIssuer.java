package com.nursena.payflow.user.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.user.application.port.out
    .AccountActionCredentialDigestPort;
import com.nursena.payflow.user.application.port.out
    .AccountActionCredentialGenerationPort;
import com.nursena.payflow.user.application.port.out
    .AccountActionCredentialRepositoryPort;
import com.nursena.payflow.user.application.port.out
    .GeneratedAccountActionCredential;
import com.nursena.payflow.user.application.port.out
    .UserRepositoryPort;
import com.nursena.payflow.user.domain.exception
    .UserNotFoundException;
import com.nursena.payflow.user.domain.model
    .AccountActionCredential;
import com.nursena.payflow.user.domain.model
    .AccountActionCredentialDigest;
import com.nursena.payflow.user.domain.model
    .AccountActionCredentialId;
import com.nursena.payflow.user.domain.model
    .AccountActionCredentialPurpose;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation
    .Transactional;

@Component
class AccountActionCredentialIssuer {

    private final UserRepositoryPort userRepository;
    private final AccountActionCredentialRepositoryPort
        credentialRepository;
    private final AccountActionCredentialGenerationPort
        credentialGeneration;
    private final AccountActionCredentialDigestPort
        credentialDigest;
    private final AccountActionCredentialLifetimePolicy
        lifetimePolicy;
    private final Clock clock;

    AccountActionCredentialIssuer(
        UserRepositoryPort userRepository,
        AccountActionCredentialRepositoryPort
            credentialRepository,
        AccountActionCredentialGenerationPort
            credentialGeneration,
        AccountActionCredentialDigestPort
            credentialDigest,
        AccountActionCredentialLifetimePolicy
            lifetimePolicy,
        Clock clock
    ) {
        this.userRepository = Objects.requireNonNull(
            userRepository,
            "userRepository must not be null"
        );
        this.credentialRepository = Objects.requireNonNull(
            credentialRepository,
            "credentialRepository must not be null"
        );
        this.credentialGeneration = Objects.requireNonNull(
            credentialGeneration,
            "credentialGeneration must not be null"
        );
        this.credentialDigest = Objects.requireNonNull(
            credentialDigest,
            "credentialDigest must not be null"
        );
        this.lifetimePolicy = Objects.requireNonNull(
            lifetimePolicy,
            "lifetimePolicy must not be null"
        );
        this.clock = Objects.requireNonNull(
            clock,
            "clock must not be null"
        );
    }

    @Transactional
    public IssuedAccountActionCredential issue(
        UUID userId,
        AccountActionCredentialPurpose purpose
    ) {
        UUID checkedUserId = Objects.requireNonNull(
            userId,
            "userId must not be null"
        );
        AccountActionCredentialPurpose checkedPurpose =
            Objects.requireNonNull(
                purpose,
                "purpose must not be null"
            );

        userRepository
            .findByIdForUpdate(checkedUserId)
            .orElseThrow(UserNotFoundException::new);

        Instant issuedAt = clock.instant()
            .truncatedTo(ChronoUnit.MICROS);

        credentialRepository.supersedeUnresolved(
            checkedUserId,
            checkedPurpose,
            issuedAt
        );

        GeneratedAccountActionCredential generated =
            credentialGeneration.generate();
        AccountActionCredentialDigest digest =
            credentialDigest.digest(generated.value());
        Instant expiresAt = lifetimePolicy.expiresAt(
            checkedPurpose,
            issuedAt
        );

        AccountActionCredential credential =
            AccountActionCredential.issue(
                AccountActionCredentialId.of(
                    UUID.randomUUID()
                ),
                checkedUserId,
                checkedPurpose,
                digest,
                issuedAt,
                expiresAt
            );

        AccountActionCredential saved =
            credentialRepository.save(credential);

        return new IssuedAccountActionCredential(
            saved.id().value(),
            generated.value(),
            saved.expiresAt()
        );
    }
}

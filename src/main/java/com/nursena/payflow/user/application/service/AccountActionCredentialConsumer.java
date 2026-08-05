package com.nursena.payflow.user.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.user.application.port.out
    .AccountActionCredentialDigestPort;
import com.nursena.payflow.user.application.port.out
    .AccountActionCredentialRepositoryPort;
import com.nursena.payflow.user.domain.exception
    .InvalidAccountActionCredentialException;
import com.nursena.payflow.user.domain.model
    .AccountActionCredential;
import com.nursena.payflow.user.domain.model
    .AccountActionCredentialDigest;
import com.nursena.payflow.user.domain.model
    .AccountActionCredentialPurpose;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation
    .Transactional;

@Component
class AccountActionCredentialConsumer {

    private final AccountActionCredentialRepositoryPort
        credentialRepository;
    private final AccountActionCredentialDigestPort
        credentialDigest;
    private final Clock clock;

    AccountActionCredentialConsumer(
        AccountActionCredentialRepositoryPort
            credentialRepository,
        AccountActionCredentialDigestPort
            credentialDigest,
        Clock clock
    ) {
        this.credentialRepository = Objects.requireNonNull(
            credentialRepository,
            "credentialRepository must not be null"
        );
        this.credentialDigest = Objects.requireNonNull(
            credentialDigest,
            "credentialDigest must not be null"
        );
        this.clock = Objects.requireNonNull(
            clock,
            "clock must not be null"
        );
    }

    @Transactional
    public UUID consume(
        String value,
        AccountActionCredentialPurpose purpose
    ) {
        AccountActionCredentialPurpose checkedPurpose =
            Objects.requireNonNull(
                purpose,
                "purpose must not be null"
            );
        AccountActionCredentialDigest digest =
            credentialDigest.digest(value);

        AccountActionCredential credential =
            credentialRepository
                .findByDigestAndPurposeForUpdate(
                    digest,
                    checkedPurpose
                )
                .orElseThrow(
                    InvalidAccountActionCredentialException::new
                );

        Instant consumedAt = clock.instant()
            .truncatedTo(ChronoUnit.MICROS);
        AccountActionCredential consumed =
            credential.consume(consumedAt);

        credentialRepository.save(consumed);

        return consumed.userId();
    }
}

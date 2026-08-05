package com.nursena.payflow.user.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.user.application.port.out
    .AccountActionCredentialRepositoryPort;
import com.nursena.payflow.user.domain.model
    .AccountActionCredential;
import com.nursena.payflow.user.domain.model
    .AccountActionCredentialDigest;
import com.nursena.payflow.user.domain.model
    .AccountActionCredentialId;
import com.nursena.payflow.user.domain.model
    .AccountActionCredentialPurpose;
import org.springframework.stereotype.Component;

@Component
class AccountActionCredentialPersistenceAdapter
    implements AccountActionCredentialRepositoryPort {

    private final SpringDataAccountActionCredentialRepository
        repository;

    AccountActionCredentialPersistenceAdapter(
        SpringDataAccountActionCredentialRepository
            repository
    ) {
        this.repository = repository;
    }

    @Override
    public AccountActionCredential save(
        AccountActionCredential credential
    ) {
        AccountActionCredentialJpaEntity saved =
            repository.saveAndFlush(
                toEntity(credential)
            );

        return toDomain(saved);
    }

    @Override
    public int supersedeUnresolved(
        UUID userId,
        AccountActionCredentialPurpose purpose,
        Instant supersededAt
    ) {
        return repository.supersedeUnresolved(
            userId,
            purpose,
            supersededAt
        );
    }

    @Override
    public Optional<AccountActionCredential>
    findByDigestAndPurposeForUpdate(
        AccountActionCredentialDigest digest,
        AccountActionCredentialPurpose purpose
    ) {
        return repository
            .findByDigestAndPurposeForUpdate(
                digest.value(),
                purpose
            )
            .map(
                AccountActionCredentialPersistenceAdapter
                    ::toDomain
            );
    }

    private static AccountActionCredentialJpaEntity
    toEntity(
        AccountActionCredential credential
    ) {
        return new AccountActionCredentialJpaEntity(
            credential.id().value(),
            credential.userId(),
            credential.purpose(),
            credential.digest().value(),
            credential.issuedAt(),
            credential.expiresAt(),
            credential.consumedAt(),
            credential.supersededAt()
        );
    }

    private static AccountActionCredential toDomain(
        AccountActionCredentialJpaEntity entity
    ) {
        return AccountActionCredential.rehydrate(
            AccountActionCredentialId.of(
                entity.getId()
            ),
            entity.getUserId(),
            entity.getPurpose(),
            AccountActionCredentialDigest.of(
                entity.getCredentialDigest()
            ),
            entity.getIssuedAt(),
            entity.getExpiresAt(),
            entity.getConsumedAt(),
            entity.getSupersededAt()
        );
    }
}

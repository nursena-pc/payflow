package com.nursena.payflow.user.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.user.application.port.out.MfaAuthenticatorRepositoryPort;
import com.nursena.payflow.user.domain.model.MfaAuthenticator;
import com.nursena.payflow.user.domain.model.ProtectedMfaSecret;
import org.springframework.stereotype.Component;

@Component
class MfaAuthenticatorPersistenceAdapter
    implements MfaAuthenticatorRepositoryPort {

    private final SpringDataMfaAuthenticatorRepository repository;

    MfaAuthenticatorPersistenceAdapter(
        SpringDataMfaAuthenticatorRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public Optional<MfaAuthenticator> findByUserId(UUID userId) {
        return repository.findById(userId)
            .map(MfaAuthenticatorPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<MfaAuthenticator> findByUserIdForUpdate(UUID userId) {
        return repository.findByUserIdForUpdate(userId)
            .map(MfaAuthenticatorPersistenceAdapter::toDomain);
    }

    @Override
    public MfaAuthenticator save(MfaAuthenticator authenticator) {
        return toDomain(
            repository.saveAndFlush(toEntity(authenticator))
        );
    }

    @Override
    public void delete(MfaAuthenticator authenticator) {
        repository.deleteById(authenticator.userId());
        repository.flush();
    }

    private static MfaAuthenticatorJpaEntity toEntity(
        MfaAuthenticator authenticator
    ) {
        return new MfaAuthenticatorJpaEntity(
            authenticator.userId(),
            authenticator.state(),
            authenticator.protectedSecret().value(),
            authenticator.enrollmentExpiresAt(),
            authenticator.activatedAt(),
            authenticator.createdAt(),
            authenticator.updatedAt()
        );
    }

    private static MfaAuthenticator toDomain(
        MfaAuthenticatorJpaEntity entity
    ) {
        return MfaAuthenticator.rehydrate(
            entity.getUserId(),
            entity.getState(),
            ProtectedMfaSecret.of(entity.getProtectedSecret()),
            entity.getEnrollmentExpiresAt(),
            entity.getActivatedAt(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}

package com.nursena.payflow.user.adapter.out.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.user.application.port.out.MfaRecoveryCodeRepositoryPort;
import com.nursena.payflow.user.domain.model.MfaRecoveryCode;
import com.nursena.payflow.user.domain.model.MfaRecoveryCodeDigest;
import org.springframework.stereotype.Component;

@Component
class MfaRecoveryCodePersistenceAdapter
    implements MfaRecoveryCodeRepositoryPort {

    private final SpringDataMfaRecoveryCodeRepository repository;

    MfaRecoveryCodePersistenceAdapter(
        SpringDataMfaRecoveryCodeRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public List<MfaRecoveryCode> saveAll(
        List<MfaRecoveryCode> recoveryCodes
    ) {
        List<MfaRecoveryCodeJpaEntity> entities = new ArrayList<>();

        for (MfaRecoveryCode recoveryCode : recoveryCodes) {
            entities.add(toEntity(recoveryCode));
        }

        return repository.saveAllAndFlush(entities)
            .stream()
            .map(MfaRecoveryCodePersistenceAdapter::toDomain)
            .toList();
    }

    @Override
    public MfaRecoveryCode save(MfaRecoveryCode recoveryCode) {
        return toDomain(
            repository.saveAndFlush(toEntity(recoveryCode))
        );
    }

    @Override
    public Optional<MfaRecoveryCode> findByUserIdAndDigestForUpdate(
        UUID userId,
        MfaRecoveryCodeDigest digest
    ) {
        return repository.findByUserIdAndDigestForUpdate(
            userId,
            digest.value()
        ).map(MfaRecoveryCodePersistenceAdapter::toDomain);
    }

    @Override
    public void deleteAllByUserId(UUID userId) {
        repository.deleteAllByUserId(userId);
    }

    private static MfaRecoveryCodeJpaEntity toEntity(
        MfaRecoveryCode recoveryCode
    ) {
        return new MfaRecoveryCodeJpaEntity(
            recoveryCode.id(),
            recoveryCode.userId(),
            recoveryCode.digest().value(),
            recoveryCode.createdAt(),
            recoveryCode.consumedAt()
        );
    }

    private static MfaRecoveryCode toDomain(
        MfaRecoveryCodeJpaEntity entity
    ) {
        return MfaRecoveryCode.rehydrate(
            entity.getId(),
            entity.getUserId(),
            MfaRecoveryCodeDigest.of(entity.getCodeDigest()),
            entity.getCreatedAt(),
            entity.getConsumedAt()
        );
    }
}

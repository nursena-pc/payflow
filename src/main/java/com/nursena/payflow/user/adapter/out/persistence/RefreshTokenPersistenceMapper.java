package com.nursena.payflow.user.adapter.out.persistence;

import com.nursena.payflow.user.domain.model.RefreshTokenDigest;
import com.nursena.payflow.user.domain.model.RefreshTokenFamily;
import com.nursena.payflow.user.domain.model.RefreshTokenFamilyId;
import com.nursena.payflow.user.domain.model.RefreshTokenRecord;
import com.nursena.payflow.user.domain.model.RefreshTokenRecordId;

final class RefreshTokenPersistenceMapper {

    private RefreshTokenPersistenceMapper() {
    }

    static RefreshTokenFamilyJpaEntity
    toFamilyEntity(
        RefreshTokenFamily family
    ) {
        return new RefreshTokenFamilyJpaEntity(
            family.id().value(),
            family.userId(),
            family.createdAt(),
            family.expiresAt(),
            family.revokedAt(),
            family.revocationReason()
        );
    }

    static RefreshTokenFamily toFamilyDomain(
        RefreshTokenFamilyJpaEntity entity
    ) {
        return RefreshTokenFamily.rehydrate(
            RefreshTokenFamilyId.of(
                entity.getId()
            ),
            entity.getUserId(),
            entity.getCreatedAt(),
            entity.getExpiresAt(),
            entity.getRevokedAt(),
            entity.getRevocationReason()
        );
    }

    static RefreshTokenRecordJpaEntity
    toRecordEntity(
        RefreshTokenRecord record
    ) {
        RefreshTokenRecordId successorId =
            record.successorId();

        return new RefreshTokenRecordJpaEntity(
            record.id().value(),
            record.familyId().value(),
            record.digest().value(),
            record.issuedAt(),
            record.expiresAt(),
            record.consumedAt(),
            successorId == null
                ? null
                : successorId.value()
        );
    }

    static RefreshTokenRecord toRecordDomain(
        RefreshTokenRecordJpaEntity entity,
        RefreshTokenFamily family
    ) {
        RefreshTokenRecordId successorId =
            entity.getSuccessorId() == null
                ? null
                : RefreshTokenRecordId.of(
                    entity.getSuccessorId()
                );

        return RefreshTokenRecord.rehydrate(
            RefreshTokenRecordId.of(
                entity.getId()
            ),
            RefreshTokenFamilyId.of(
                entity.getFamilyId()
            ),
            RefreshTokenDigest.of(
                entity.getTokenDigest()
            ),
            entity.getIssuedAt(),
            entity.getExpiresAt(),
            entity.getConsumedAt(),
            successorId,
            family
        );
    }
}

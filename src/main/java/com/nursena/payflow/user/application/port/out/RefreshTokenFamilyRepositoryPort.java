package com.nursena.payflow.user.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.user.domain.model.RefreshTokenFamily;
import com.nursena.payflow.user.domain.model.RefreshTokenFamilyId;
import com.nursena.payflow.user.domain.model.RefreshTokenFamilyRevocationReason;

public interface RefreshTokenFamilyRepositoryPort {

    RefreshTokenFamily save(
        RefreshTokenFamily family
    );

    Optional<RefreshTokenFamily>
    findByIdForUpdate(
        RefreshTokenFamilyId familyId
    );

    int revokeAllActiveByUserId(
        UUID userId,
        Instant revokedAt,
        RefreshTokenFamilyRevocationReason reason
    );
}

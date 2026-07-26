package com.nursena.payflow.user.application.port.out;

import java.util.Optional;

import com.nursena.payflow.user.domain.model.RefreshTokenDigest;
import com.nursena.payflow.user.domain.model.RefreshTokenRecord;
import com.nursena.payflow.user.domain.model.RefreshTokenRecordId;

public interface RefreshTokenRecordRepositoryPort {

    RefreshTokenRecord save(
        RefreshTokenRecord record
    );

    Optional<RefreshTokenRecord>
    findByDigestForUpdate(
        RefreshTokenDigest digest
    );

    Optional<RefreshTokenRecord> findById(
        RefreshTokenRecordId recordId
    );
}

package com.nursena.payflow.user.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.user.domain.model.MfaRecoveryCode;
import com.nursena.payflow.user.domain.model.MfaRecoveryCodeDigest;

public interface MfaRecoveryCodeRepositoryPort {

    List<MfaRecoveryCode> saveAll(List<MfaRecoveryCode> recoveryCodes);

    MfaRecoveryCode save(MfaRecoveryCode recoveryCode);

    Optional<MfaRecoveryCode> findByUserIdAndDigestForUpdate(
        UUID userId,
        MfaRecoveryCodeDigest digest
    );
}

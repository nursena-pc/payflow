package com.nursena.payflow.user.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.user.domain.model.MfaLoginChallenge;
import com.nursena.payflow.user.domain.model.MfaLoginChallengeDigest;

public interface MfaLoginChallengeRepositoryPort {

    MfaLoginChallenge save(MfaLoginChallenge challenge);

    int supersedePendingByUserId(UUID userId, Instant resolvedAt);

    Optional<UUID> findUserIdByDigest(MfaLoginChallengeDigest digest);

    Optional<MfaLoginChallenge> findByDigestForUpdate(
        MfaLoginChallengeDigest digest
    );
}

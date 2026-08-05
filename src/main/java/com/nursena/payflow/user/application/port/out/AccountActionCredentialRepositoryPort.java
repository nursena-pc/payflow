package com.nursena.payflow.user.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.user.domain.model
    .AccountActionCredential;
import com.nursena.payflow.user.domain.model
    .AccountActionCredentialDigest;
import com.nursena.payflow.user.domain.model
    .AccountActionCredentialPurpose;

public interface AccountActionCredentialRepositoryPort {

    AccountActionCredential save(
        AccountActionCredential credential
    );

    int supersedeUnresolved(
        UUID userId,
        AccountActionCredentialPurpose purpose,
        Instant supersededAt
    );

    Optional<UUID> findUserIdByDigestAndPurpose(
        AccountActionCredentialDigest digest,
        AccountActionCredentialPurpose purpose
    );

    Optional<AccountActionCredential>
    findByDigestAndPurposeForUpdate(
        AccountActionCredentialDigest digest,
        AccountActionCredentialPurpose purpose
    );
}

package com.nursena.payflow.user.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.user.domain.model.MfaAuthenticator;

public interface MfaAuthenticatorRepositoryPort {

    Optional<MfaAuthenticator> findByUserId(UUID userId);

    Optional<MfaAuthenticator> findByUserIdForUpdate(UUID userId);

    MfaAuthenticator save(MfaAuthenticator authenticator);

    void delete(MfaAuthenticator authenticator);
}

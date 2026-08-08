package com.nursena.payflow.user.application.service;

import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.user.application.port.in.GetMfaStatusResult;
import com.nursena.payflow.user.application.port.in.GetMfaStatusUseCase;
import com.nursena.payflow.user.application.port.out.MfaAuthenticatorRepositoryPort;
import com.nursena.payflow.user.application.port.out.UserRepositoryPort;
import com.nursena.payflow.user.domain.exception.UserNotFoundException;
import com.nursena.payflow.user.domain.model.MfaAuthenticator;
import com.nursena.payflow.user.domain.model.MfaLifecycleState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GetMfaStatusService
    implements GetMfaStatusUseCase {

    private final UserRepositoryPort userRepository;
    private final MfaAuthenticatorRepositoryPort authenticatorRepository;

    public GetMfaStatusService(
        UserRepositoryPort userRepository,
        MfaAuthenticatorRepositoryPort authenticatorRepository
    ) {
        this.userRepository = userRepository;
        this.authenticatorRepository = authenticatorRepository;
    }

    @Override
    public GetMfaStatusResult getStatus(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");

        userRepository.findById(userId)
            .orElseThrow(UserNotFoundException::new);

        return authenticatorRepository.findByUserId(userId)
            .map(GetMfaStatusService::toResult)
            .orElseGet(() -> new GetMfaStatusResult(
                MfaLifecycleState.DISABLED,
                null,
                null
            ));
    }

    private static GetMfaStatusResult toResult(
        MfaAuthenticator authenticator
    ) {
        return new GetMfaStatusResult(
            authenticator.state(),
            authenticator.enrollmentExpiresAt(),
            authenticator.activatedAt()
        );
    }
}

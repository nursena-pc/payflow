package com.nursena.payflow.user.application.service;

import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.user.application.port.in.CancelMfaEnrollmentUseCase;
import com.nursena.payflow.user.application.port.out.MfaAuthenticatorRepositoryPort;
import com.nursena.payflow.user.application.port.out.UserRepositoryPort;
import com.nursena.payflow.user.domain.exception.MfaStateConflictException;
import com.nursena.payflow.user.domain.exception.UserNotFoundException;
import com.nursena.payflow.user.domain.model.MfaAuthenticator;
import com.nursena.payflow.user.domain.model.MfaLifecycleState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CancelMfaEnrollmentService
    implements CancelMfaEnrollmentUseCase {

    private final UserRepositoryPort userRepository;
    private final MfaAuthenticatorRepositoryPort authenticatorRepository;

    public CancelMfaEnrollmentService(
        UserRepositoryPort userRepository,
        MfaAuthenticatorRepositoryPort authenticatorRepository
    ) {
        this.userRepository = userRepository;
        this.authenticatorRepository = authenticatorRepository;
    }

    @Override
    public void cancel(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");

        userRepository.findByIdForUpdate(userId)
            .orElseThrow(UserNotFoundException::new);

        MfaAuthenticator authenticator = authenticatorRepository
            .findByUserIdForUpdate(userId)
            .orElseThrow(MfaStateConflictException::new);

        if (authenticator.state() != MfaLifecycleState.PENDING) {
            throw new MfaStateConflictException();
        }

        authenticatorRepository.delete(authenticator);
    }
}

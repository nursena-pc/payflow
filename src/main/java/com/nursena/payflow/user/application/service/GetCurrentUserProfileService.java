package com.nursena.payflow.user.application.service;

import java.util.Objects;
import java.util.UUID;

import com.nursena.payflow.user.application.port.in.GetCurrentUserProfileResult;
import com.nursena.payflow.user.application.port.in.GetCurrentUserProfileUseCase;
import com.nursena.payflow.user.application.port.out.UserRepositoryPort;
import com.nursena.payflow.user.domain.exception.UserNotFoundException;
import com.nursena.payflow.user.domain.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GetCurrentUserProfileService
    implements GetCurrentUserProfileUseCase {

    private final UserRepositoryPort userRepository;

    public GetCurrentUserProfileService(
        UserRepositoryPort userRepository
    ) {
        this.userRepository = userRepository;
    }

    @Override
    public GetCurrentUserProfileResult getProfile(UUID userId) {
        Objects.requireNonNull(
            userId,
            "userId must not be null"
        );

        User user = userRepository.findById(userId)
            .orElseThrow(UserNotFoundException::new);

        return new GetCurrentUserProfileResult(
            user.id(),
            user.email().value(),
            user.role(),
            user.status(),
            user.createdAt()
        );
    }
}

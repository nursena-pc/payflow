package com.nursena.payflow.user.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.user.domain.model.EmailAddress;
import com.nursena.payflow.user.domain.model.User;

public interface UserRepositoryPort {

    boolean existsByEmail(EmailAddress email);

    Optional<User> findByEmail(EmailAddress email);

    Optional<User> findById(UUID userId);

    Optional<User> findByIdForUpdate(UUID userId);

    User save(User user);
}

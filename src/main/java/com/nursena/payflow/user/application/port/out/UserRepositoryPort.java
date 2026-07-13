package com.nursena.payflow.user.application.port.out;

import java.util.Optional;

import com.nursena.payflow.user.domain.model.EmailAddress;
import com.nursena.payflow.user.domain.model.User;

public interface UserRepositoryPort {

    boolean existsByEmail(EmailAddress email);

    Optional<User> findByEmail(EmailAddress email);

    User save(User user);
}

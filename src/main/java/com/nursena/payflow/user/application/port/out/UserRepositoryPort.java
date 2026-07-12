package com.nursena.payflow.user.application.port.out;

import com.nursena.payflow.user.domain.model.EmailAddress;
import com.nursena.payflow.user.domain.model.User;

public interface UserRepositoryPort {

    boolean existsByEmail(EmailAddress email);

    User save(User user);
}

package com.nursena.payflow.user.application.port.in;

import java.util.UUID;

public interface RegisterUserUseCase {

    UUID register(RegisterUserCommand command);
}

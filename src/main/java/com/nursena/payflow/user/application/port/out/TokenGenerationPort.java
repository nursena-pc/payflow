package com.nursena.payflow.user.application.port.out;

import com.nursena.payflow.user.domain.model.User;

public interface TokenGenerationPort {

    GeneratedAccessToken generate(User user);
}

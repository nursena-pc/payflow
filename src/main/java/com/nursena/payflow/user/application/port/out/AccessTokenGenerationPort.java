package com.nursena.payflow.user.application.port.out;

import com.nursena.payflow.user.domain.model.User;

public interface AccessTokenGenerationPort {

    GeneratedAccessToken generate(User user);
}

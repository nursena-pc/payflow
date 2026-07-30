package com.nursena.payflow.user.application.port.out;

import com.nursena.payflow.user.domain.model.EmailAddress;

public interface LoginRateLimitPort {

    LoginRateLimitDecision evaluate(
        LoginRateLimitRequest request
    );

    void resetIdentity(
        EmailAddress identity
    );
}

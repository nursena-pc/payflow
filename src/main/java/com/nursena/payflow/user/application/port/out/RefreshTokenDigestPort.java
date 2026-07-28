package com.nursena.payflow.user.application.port.out;

import com.nursena.payflow.user.domain.model.RefreshTokenDigest;

public interface RefreshTokenDigestPort {

    RefreshTokenDigest digest(
        String refreshToken
    );
}

package com.nursena.payflow.user.application.port.out;

import java.time.Instant;

public interface TotpVerificationPort {

    boolean verify(
        byte[] secret,
        String code,
        Instant now
    );
}

package com.nursena.payflow.user.application.port.out;

import com.nursena.payflow.user.domain.model.MfaRecoveryCodeDigest;

public interface MfaRecoveryCodeDigestPort {

    MfaRecoveryCodeDigest digest(String value);
}

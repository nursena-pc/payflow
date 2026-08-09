package com.nursena.payflow.user.application.port.out;

import com.nursena.payflow.user.domain.model.MfaLoginChallengeDigest;

public interface MfaLoginChallengeDigestPort {
    MfaLoginChallengeDigest digest(String value);
}

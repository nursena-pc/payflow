package com.nursena.payflow.user.application.port.out;

import com.nursena.payflow.user.domain.model
    .AccountActionCredentialDigest;

public interface AccountActionCredentialDigestPort {

    AccountActionCredentialDigest digest(
        String credential
    );
}

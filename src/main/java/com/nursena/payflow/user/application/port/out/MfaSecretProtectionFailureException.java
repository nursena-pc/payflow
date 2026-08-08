package com.nursena.payflow.user.application.port.out;

public final class MfaSecretProtectionFailureException extends RuntimeException {

    public MfaSecretProtectionFailureException() {
        super("MFA secret protection failed.");
    }

    public MfaSecretProtectionFailureException(Throwable cause) {
        super("MFA secret protection failed.", cause);
    }
}

package com.nursena.payflow.user.application.port.out;

public interface PasswordVerificationPort {

    boolean matches(
        String rawPassword,
        String passwordHash
    );
}

package com.nursena.payflow.user.adapter.out.security;

import com.nursena.payflow.user.application.port.out.PasswordHashingPort;
import com.nursena.payflow.user.application.port.out.PasswordVerificationPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class BCryptPasswordHashingAdapter
        implements PasswordHashingPort, PasswordVerificationPort {

    private final PasswordEncoder passwordEncoder;

    BCryptPasswordHashingAdapter(
            PasswordEncoder passwordEncoder
    ) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String hash(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(
            String rawPassword,
            String passwordHash
    ) {
        return passwordEncoder.matches(
                rawPassword,
                passwordHash
        );
    }
}

package com.nursena.payflow.user.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class BCryptPasswordHashingAdapterTest {

    @Test
    void shouldHashPasswordUsingBCrypt() {
        PasswordEncoder encoder = new BCryptPasswordEncoder(4);
        BCryptPasswordHashingAdapter adapter =
                new BCryptPasswordHashingAdapter(encoder);

        String rawPassword = "StrongPassword123!";

        String passwordHash = adapter.hash(rawPassword);

        assertThat(passwordHash).isNotEqualTo(rawPassword);
        assertThat(
                encoder.matches(rawPassword, passwordHash)
        ).isTrue();
    }

    @Test
    void shouldVerifyMatchingPassword() {
        PasswordEncoder encoder = new BCryptPasswordEncoder(4);
        BCryptPasswordHashingAdapter adapter =
                new BCryptPasswordHashingAdapter(encoder);

        String rawPassword = "StrongPassword123!";
        String passwordHash = encoder.encode(rawPassword);

        boolean matches = adapter.matches(
                rawPassword,
                passwordHash
        );

        assertThat(matches).isTrue();
    }
}

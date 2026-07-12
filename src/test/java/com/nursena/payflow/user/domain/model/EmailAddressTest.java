package com.nursena.payflow.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nursena.payflow.user.domain.exception.InvalidEmailException;
import org.junit.jupiter.api.Test;

class EmailAddressTest {

    @Test
    void shouldNormalizeEmailAddress() {
        EmailAddress email = EmailAddress.of("  Nursena@Example.COM  ");

        assertThat(email.value()).isEqualTo("nursena@example.com");
    }

    @Test
    void shouldRejectEmailWithoutAtSign() {
        assertThatThrownBy(() -> EmailAddress.of("nursena.example.com"))
            .isInstanceOf(InvalidEmailException.class);
    }

    @Test
    void shouldRejectBlankEmail() {
        assertThatThrownBy(() -> EmailAddress.of(" "))
            .isInstanceOf(InvalidEmailException.class);
    }
}

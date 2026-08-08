package com.nursena.payflow.user.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class TotpPropertiesTest {

    @Test
    void shouldRetainIssuerAndEnrollmentTtl() {
        TotpProperties properties = new TotpProperties(" PayFlow ", Duration.ofMinutes(10));
        assertThat(properties.issuer()).isEqualTo("PayFlow");
        assertThat(properties.enrollmentTtl()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void shouldRejectBlankIssuer() {
        assertThatThrownBy(() -> new TotpProperties(" ", Duration.ofMinutes(10)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectEnrollmentTtlLongerThanOneHour() {
        assertThatThrownBy(() -> new TotpProperties("PayFlow", Duration.ofHours(2)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

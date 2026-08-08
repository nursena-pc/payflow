package com.nursena.payflow.user.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class HmacSha1TotpVerificationAdapterTest {

    private static final byte[] RFC_SECRET =
        "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private final HmacSha1TotpVerificationAdapter adapter =
        new HmacSha1TotpVerificationAdapter();

    @Test
    void shouldVerifyRfc4226CounterOneAtTotpSecondFiftyNine() {
        assertThat(adapter.verify(RFC_SECRET, "287082", Instant.ofEpochSecond(59))).isTrue();
    }

    @Test
    void shouldAcceptPreviousTimeStep() {
        assertThat(adapter.verify(RFC_SECRET, "755224", Instant.ofEpochSecond(31))).isTrue();
    }

    @Test
    void shouldAcceptNextTimeStep() {
        assertThat(adapter.verify(RFC_SECRET, "359152", Instant.ofEpochSecond(31))).isTrue();
    }

    @Test
    void shouldRejectCodeOutsideOneStepWindow() {
        assertThat(adapter.verify(RFC_SECRET, "969429", Instant.ofEpochSecond(31))).isFalse();
    }

    @Test
    void shouldRejectMalformedCode() {
        assertThat(adapter.verify(RFC_SECRET, "12A456", Instant.ofEpochSecond(59))).isFalse();
    }
}

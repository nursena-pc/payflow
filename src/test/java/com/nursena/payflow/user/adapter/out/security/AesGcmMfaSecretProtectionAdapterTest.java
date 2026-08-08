package com.nursena.payflow.user.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import com.nursena.payflow.user.application.port.out.MfaSecretProtectionFailureException;
import com.nursena.payflow.user.domain.model.ProtectedMfaSecret;
import org.junit.jupiter.api.Test;

class AesGcmMfaSecretProtectionAdapterTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final byte[] SECRET = "12345678901234567890".getBytes();

    @Test
    void shouldRoundTripSecret() {
        AesGcmMfaSecretProtectionAdapter adapter = adapter((byte) 1);
        ProtectedMfaSecret protectedSecret = adapter.protect(USER_ID, SECRET);
        assertThat(containsSubsequence(protectedSecret.value(), SECRET)).isFalse();
        assertThat(adapter.reveal(USER_ID, protectedSecret)).isEqualTo(SECRET);
    }

    @Test
    void shouldUseFreshNonceForEveryProtection() {
        AesGcmMfaSecretProtectionAdapter adapter = adapter((byte) 2);
        assertThat(adapter.protect(USER_ID, SECRET).value())
            .isNotEqualTo(adapter.protect(USER_ID, SECRET).value());
    }

    @Test
    void shouldBindCiphertextToUserContext() {
        AesGcmMfaSecretProtectionAdapter adapter = adapter((byte) 3);
        ProtectedMfaSecret protectedSecret = adapter.protect(USER_ID, SECRET);
        assertThatThrownBy(() -> adapter.reveal(UUID.randomUUID(), protectedSecret))
            .isInstanceOf(MfaSecretProtectionFailureException.class);
    }

    @Test
    void shouldRejectTamperedCiphertext() {
        AesGcmMfaSecretProtectionAdapter adapter = adapter((byte) 4);
        byte[] value = adapter.protect(USER_ID, SECRET).value();
        value[value.length - 1] ^= 1;
        assertThatThrownBy(() -> adapter.reveal(USER_ID, ProtectedMfaSecret.of(value)))
            .isInstanceOf(MfaSecretProtectionFailureException.class);
    }

    @Test
    void shouldRejectShortPlaintextSecret() {
        assertThatThrownBy(() -> adapter((byte) 5).protect(USER_ID, new byte[19]))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static boolean containsSubsequence(byte[] value, byte[] candidate) {
        if (candidate.length == 0) {
            return true;
        }

        if (candidate.length > value.length) {
            return false;
        }

        for (int offset = 0; offset <= value.length - candidate.length; offset++) {
            boolean match = true;

            for (int index = 0; index < candidate.length; index++) {
                if (value[offset + index] != candidate[index]) {
                    match = false;
                    break;
                }
            }

            if (match) {
                return true;
            }
        }

        return false;
    }

    private static AesGcmMfaSecretProtectionAdapter adapter(byte fill) {
        byte[] key = new byte[32];
        Arrays.fill(key, fill);
        return new AesGcmMfaSecretProtectionAdapter(
            new SecretKeySpec(key, "AES"),
            new SecureRandom()
        );
    }
}

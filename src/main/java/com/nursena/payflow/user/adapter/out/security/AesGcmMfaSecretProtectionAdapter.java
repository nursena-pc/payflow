package com.nursena.payflow.user.adapter.out.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import com.nursena.payflow.user.application.port.out.MfaSecretProtectionFailureException;
import com.nursena.payflow.user.application.port.out.MfaSecretProtectionPort;
import com.nursena.payflow.user.domain.model.ProtectedMfaSecret;

final class AesGcmMfaSecretProtectionAdapter
    implements MfaSecretProtectionPort {

    private static final byte FORMAT_VERSION = 1;
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final String AAD_FORMAT = "payflow-mfa-totp-v1";

    private final SecretKey key;
    private final SecureRandom secureRandom;

    AesGcmMfaSecretProtectionAdapter(
        SecretKey key,
        SecureRandom secureRandom
    ) {
        this.key = Objects.requireNonNull(key, "key must not be null");
        this.secureRandom = Objects.requireNonNull(
            secureRandom,
            "secureRandom must not be null"
        );
    }

    @Override
    public ProtectedMfaSecret protect(
        UUID userId,
        byte[] plaintextSecret
    ) {
        UUID checkedUserId = Objects.requireNonNull(
            userId,
            "userId must not be null"
        );

        if (plaintextSecret == null || plaintextSecret.length < 20) {
            throw new IllegalArgumentException(
                "plaintextSecret must contain at least 160 bits"
            );
        }

        byte[] nonce = new byte[NONCE_LENGTH];
        secureRandom.nextBytes(nonce);

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                Cipher.ENCRYPT_MODE,
                key,
                new GCMParameterSpec(TAG_LENGTH_BITS, nonce)
            );
            cipher.updateAAD(authenticatedData(checkedUserId));

            byte[] ciphertext = cipher.doFinal(
                Arrays.copyOf(
                    plaintextSecret,
                    plaintextSecret.length
                )
            );

            ByteBuffer encoded = ByteBuffer.allocate(
                1 + NONCE_LENGTH + ciphertext.length
            );
            encoded.put(FORMAT_VERSION);
            encoded.put(nonce);
            encoded.put(ciphertext);

            return ProtectedMfaSecret.of(encoded.array());
        }
        catch (GeneralSecurityException exception) {
            throw new MfaSecretProtectionFailureException(exception);
        }
    }

    @Override
    public byte[] reveal(
        UUID userId,
        ProtectedMfaSecret protectedSecret
    ) {
        UUID checkedUserId = Objects.requireNonNull(
            userId,
            "userId must not be null"
        );
        Objects.requireNonNull(
            protectedSecret,
            "protectedSecret must not be null"
        );

        byte[] encoded = protectedSecret.value();
        if (encoded.length <= 1 + NONCE_LENGTH + 16) {
            throw new MfaSecretProtectionFailureException();
        }

        ByteBuffer buffer = ByteBuffer.wrap(encoded);
        if (buffer.get() != FORMAT_VERSION) {
            throw new MfaSecretProtectionFailureException();
        }

        byte[] nonce = new byte[NONCE_LENGTH];
        buffer.get(nonce);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                new GCMParameterSpec(TAG_LENGTH_BITS, nonce)
            );
            cipher.updateAAD(authenticatedData(checkedUserId));
            return cipher.doFinal(ciphertext);
        }
        catch (GeneralSecurityException exception) {
            throw new MfaSecretProtectionFailureException(exception);
        }
    }

    private static byte[] authenticatedData(UUID userId) {
        return (
            AAD_FORMAT
                + ":"
                + userId
        ).getBytes(StandardCharsets.UTF_8);
    }
}

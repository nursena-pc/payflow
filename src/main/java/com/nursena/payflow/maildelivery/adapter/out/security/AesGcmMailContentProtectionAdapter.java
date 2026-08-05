package com.nursena.payflow.maildelivery.adapter.out.security;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import com.nursena.payflow.maildelivery.application.port.out.MailContentProtectionPort;
import com.nursena.payflow.maildelivery.domain.model.MailContentProtectionContext;
import com.nursena.payflow.maildelivery.domain.model.ProtectedMailContent;

final class AesGcmMailContentProtectionAdapter
    implements MailContentProtectionPort {

    private static final byte FORMAT_VERSION = 1;
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final String AAD_FORMAT = "payflow-mail-outbox-v1";

    private final SecretKey key;
    private final SecureRandom secureRandom;

    AesGcmMailContentProtectionAdapter(
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
    public ProtectedMailContent protect(
        MailContentProtectionContext context,
        String plaintext
    ) {
        MailContentProtectionContext checkedContext = Objects.requireNonNull(
            context,
            "context must not be null"
        );
        Objects.requireNonNull(plaintext, "plaintext must not be null");
        if (plaintext.isBlank()) {
            throw new IllegalArgumentException("plaintext must not be blank");
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
            cipher.updateAAD(authenticatedData(checkedContext));
            byte[] ciphertext = cipher.doFinal(
                plaintext.getBytes(StandardCharsets.UTF_8)
            );
            ByteBuffer encoded = ByteBuffer.allocate(
                1 + NONCE_LENGTH + ciphertext.length
            );
            encoded.put(FORMAT_VERSION);
            encoded.put(nonce);
            encoded.put(ciphertext);
            return ProtectedMailContent.of(encoded.array());
        } catch (GeneralSecurityException exception) {
            throw new MailContentProtectionException(exception);
        }
    }

    @Override
    public String reveal(
        MailContentProtectionContext context,
        ProtectedMailContent protectedContent
    ) {
        MailContentProtectionContext checkedContext = Objects.requireNonNull(
            context,
            "context must not be null"
        );
        Objects.requireNonNull(
            protectedContent,
            "protectedContent must not be null"
        );
        byte[] encoded = protectedContent.value();
        if (encoded.length <= 1 + NONCE_LENGTH) {
            throw new MailContentProtectionException();
        }
        ByteBuffer buffer = ByteBuffer.wrap(encoded);
        byte version = buffer.get();
        if (version != FORMAT_VERSION) {
            throw new MailContentProtectionException();
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
            cipher.updateAAD(authenticatedData(checkedContext));
            return new String(
                cipher.doFinal(ciphertext),
                StandardCharsets.UTF_8
            );
        } catch (GeneralSecurityException exception) {
            throw new MailContentProtectionException(exception);
        }
    }

    private static byte[] authenticatedData(
        MailContentProtectionContext context
    ) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                writeUtf8(output, AAD_FORMAT);
                output.writeLong(context.messageId().getMostSignificantBits());
                output.writeLong(context.messageId().getLeastSignificantBits());
                output.writeLong(context.userId().getMostSignificantBits());
                output.writeLong(context.userId().getLeastSignificantBits());
                writeUtf8(output, context.purpose().name());
                writeUtf8(output, context.recipient());
                writeUtf8(output, context.subject());
            }
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new MailContentProtectionException(exception);
        }
    }

    private static void writeUtf8(
        DataOutputStream output,
        String value
    ) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }
}

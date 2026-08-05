package com.nursena.payflow.user.adapter.out.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

import com.nursena.payflow.user.application.port.out
    .AccountActionCredentialDigestPort;
import com.nursena.payflow.user.domain.exception
    .InvalidAccountActionCredentialException;
import com.nursena.payflow.user.domain.model
    .AccountActionCredentialDigest;

final class Sha256AccountActionCredentialDigestAdapter
    implements AccountActionCredentialDigestPort {

    static final int CREDENTIAL_LENGTH_BYTES = 32;
    static final int ENCODED_CREDENTIAL_LENGTH = 43;

    private static final String SHA_256 = "SHA-256";

    private static final Base64.Decoder CREDENTIAL_DECODER =
        Base64.getUrlDecoder();

    private static final Base64.Encoder CREDENTIAL_ENCODER =
        Base64.getUrlEncoder()
            .withoutPadding();

    @Override
    public AccountActionCredentialDigest digest(
        String credential
    ) {
        byte[] decodedCredential =
            decodeCanonicalCredential(credential);

        try {
            byte[] digestBytes = messageDigest().digest(
                decodedCredential
            );

            return AccountActionCredentialDigest.of(
                digestBytes
            );
        } finally {
            Arrays.fill(
                decodedCredential,
                (byte) 0
            );
        }
    }

    private static byte[] decodeCanonicalCredential(
        String credential
    ) {
        if (
            credential == null
                || credential.length()
                    != ENCODED_CREDENTIAL_LENGTH
                || !containsOnlyBase64UrlCharacters(
                    credential
                )
        ) {
            throw new
                InvalidAccountActionCredentialException();
        }

        byte[] decodedCredential;

        try {
            decodedCredential = CREDENTIAL_DECODER.decode(
                credential
            );
        } catch (IllegalArgumentException ignored) {
            throw new
                InvalidAccountActionCredentialException();
        }

        if (
            decodedCredential.length
                != CREDENTIAL_LENGTH_BYTES
        ) {
            Arrays.fill(
                decodedCredential,
                (byte) 0
            );
            throw new
                InvalidAccountActionCredentialException();
        }

        String canonicalValue =
            CREDENTIAL_ENCODER.encodeToString(
                decodedCredential
            );

        if (!canonicalValue.equals(credential)) {
            Arrays.fill(
                decodedCredential,
                (byte) 0
            );
            throw new
                InvalidAccountActionCredentialException();
        }

        return decodedCredential;
    }

    private static boolean
    containsOnlyBase64UrlCharacters(
        String value
    ) {
        for (
            int index = 0;
            index < value.length();
            index++
        ) {
            char character = value.charAt(index);

            boolean supported =
                character >= 'A'
                    && character <= 'Z'
                    || character >= 'a'
                    && character <= 'z'
                    || character >= '0'
                    && character <= '9'
                    || character == '-'
                    || character == '_';

            if (!supported) {
                return false;
            }
        }

        return true;
    }

    private static MessageDigest messageDigest() {
        try {
            return MessageDigest.getInstance(SHA_256);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                "SHA-256 message digest is not available.",
                exception
            );
        }
    }
}

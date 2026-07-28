package com.nursena.payflow.user.adapter.out.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

import com.nursena.payflow.user.application.port.out.RefreshTokenDigestPort;
import com.nursena.payflow.user.domain.exception.InvalidRefreshTokenException;
import com.nursena.payflow.user.domain.model.RefreshTokenDigest;

final class Sha256RefreshTokenDigestAdapter
    implements RefreshTokenDigestPort {

    static final int TOKEN_LENGTH_BYTES = 32;
    static final int ENCODED_TOKEN_LENGTH = 43;

    private static final String SHA_256 =
        "SHA-256";

    private static final Base64.Decoder TOKEN_DECODER =
        Base64.getUrlDecoder();

    private static final Base64.Encoder TOKEN_ENCODER =
        Base64.getUrlEncoder()
            .withoutPadding();

    @Override
    public RefreshTokenDigest digest(
        String refreshToken
    ) {
        byte[] decodedToken =
            decodeCanonicalToken(
                refreshToken
            );

        try {
            byte[] digestBytes =
                messageDigest().digest(
                    decodedToken
                );

            return RefreshTokenDigest.of(
                digestBytes
            );
        } finally {
            Arrays.fill(
                decodedToken,
                (byte) 0
            );
        }
    }

    private static byte[] decodeCanonicalToken(
        String refreshToken
    ) {
        if (
            refreshToken == null
                || refreshToken.length()
                != ENCODED_TOKEN_LENGTH
                || !containsOnlyBase64UrlCharacters(
                    refreshToken
                )
        ) {
            throw new InvalidRefreshTokenException();
        }

        byte[] decodedToken;

        try {
            decodedToken =
                TOKEN_DECODER.decode(
                    refreshToken
                );
        } catch (IllegalArgumentException ignored) {
            throw new InvalidRefreshTokenException();
        }

        if (
            decodedToken.length
                != TOKEN_LENGTH_BYTES
        ) {
            Arrays.fill(
                decodedToken,
                (byte) 0
            );

            throw new InvalidRefreshTokenException();
        }

        String canonicalValue =
            TOKEN_ENCODER.encodeToString(
                decodedToken
            );

        if (!canonicalValue.equals(refreshToken)) {
            Arrays.fill(
                decodedToken,
                (byte) 0
            );

            throw new InvalidRefreshTokenException();
        }

        return decodedToken;
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
            char character =
                value.charAt(index);

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
            return MessageDigest.getInstance(
                SHA_256
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                "SHA-256 message digest "
                    + "is not available.",
                exception
            );
        }
    }
}

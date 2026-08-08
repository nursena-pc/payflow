package com.nursena.payflow.user.adapter.out.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.nursena.payflow.user.application.port.out.TotpVerificationPort;

final class HmacSha1TotpVerificationAdapter
    implements TotpVerificationPort {

    private static final long PERIOD_SECONDS = 30L;
    private static final int WINDOW = 1;
    private static final int MODULUS = 1_000_000;

    @Override
    public boolean verify(
        byte[] secret,
        String code,
        Instant now
    ) {
        if (secret == null || secret.length < 20) {
            return false;
        }

        if (code == null || !code.matches("[0-9]{6}")) {
            return false;
        }

        Instant checkedAt = Objects.requireNonNull(
            now,
            "now must not be null"
        );
        long currentCounter = Math.floorDiv(
            checkedAt.getEpochSecond(),
            PERIOD_SECONDS
        );

        byte[] supplied = code.getBytes(StandardCharsets.US_ASCII);

        for (int offset = -WINDOW; offset <= WINDOW; offset++) {
            long counter = currentCounter + offset;
            if (counter < 0) {
                continue;
            }

            byte[] candidate = generateCode(secret, counter)
                .getBytes(StandardCharsets.US_ASCII);

            if (MessageDigest.isEqual(candidate, supplied)) {
                return true;
            }
        }

        return false;
    }

    private static String generateCode(
        byte[] secret,
        long counter
    ) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));

            byte[] digest = mac.doFinal(
                ByteBuffer.allocate(Long.BYTES)
                    .putLong(counter)
                    .array()
            );

            int offset = digest[digest.length - 1] & 0x0f;
            int binary =
                ((digest[offset] & 0x7f) << 24)
                    | ((digest[offset + 1] & 0xff) << 16)
                    | ((digest[offset + 2] & 0xff) << 8)
                    | (digest[offset + 3] & 0xff);

            return String.format("%06d", binary % MODULUS);
        }
        catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                "TOTP verification algorithm is unavailable.",
                exception
            );
        }
    }
}

package com.nursena.payflow.user.adapter.out.security;

final class Base32Codec {

    private static final char[] ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private Base32Codec() {
    }

    static String encode(byte[] source) {
        if (source == null || source.length == 0) {
            throw new IllegalArgumentException(
                "source must not be empty"
            );
        }

        StringBuilder encoded = new StringBuilder(
            (source.length * 8 + 4) / 5
        );

        int buffer = 0;
        int bitsLeft = 0;

        for (byte value : source) {
            buffer = (buffer << 8) | (value & 0xff);
            bitsLeft += 8;

            while (bitsLeft >= 5) {
                encoded.append(
                    ALPHABET[(buffer >> (bitsLeft - 5)) & 0x1f]
                );
                bitsLeft -= 5;
            }
        }

        if (bitsLeft > 0) {
            encoded.append(
                ALPHABET[(buffer << (5 - bitsLeft)) & 0x1f]
            );
        }

        return encoded.toString();
    }
}

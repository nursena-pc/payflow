package com.nursena.payflow.clientcontext.domain;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

public final class IpAddress {

    private static final Pattern IPV6_LITERAL_CHARACTERS =
        Pattern.compile("[0-9A-Fa-f:.]+");

    private final byte[] bytes;
    private final String value;

    private IpAddress(byte[] bytes) {
        this.bytes = Arrays.copyOf(
            bytes,
            bytes.length
        );
        this.value = format(bytes);
    }

    public static IpAddress parse(String rawValue) {
        Objects.requireNonNull(
            rawValue,
            "IP address must not be null"
        );

        if (
            rawValue.isBlank()
                || !rawValue.equals(rawValue.trim())
        ) {
            throw invalid(rawValue);
        }

        byte[] parsedBytes =
            rawValue.indexOf(':') >= 0
                ? parseIpv6(rawValue)
                : parseIpv4(rawValue);

        return new IpAddress(parsedBytes);
    }

    static IpAddress fromBytes(byte[] bytes) {
        Objects.requireNonNull(
            bytes,
            "IP address bytes must not be null"
        );

        if (
            bytes.length != Integer.BYTES
                && bytes.length != 16
        ) {
            throw new IllegalArgumentException(
                "IP address must contain 4 or 16 bytes"
            );
        }

        return new IpAddress(bytes);
    }

    public String value() {
        return value;
    }

    public boolean isIpv4() {
        return bytes.length == Integer.BYTES;
    }

    public boolean isIpv6() {
        return bytes.length == 16;
    }

    public int bitLength() {
        return bytes.length * Byte.SIZE;
    }

    public byte[] bytes() {
        return Arrays.copyOf(
            bytes,
            bytes.length
        );
    }

    private static byte[] parseIpv4(String value) {
        String[] octets =
            value.split(
                "\\.",
                -1
            );

        if (octets.length != 4) {
            throw invalid(value);
        }

        byte[] result =
            new byte[Integer.BYTES];

        for (
            int index = 0;
            index < octets.length;
            index++
        ) {
            String octet =
                octets[index];

            if (
                octet.isEmpty()
                    || octet.length() > 3
                    || (
                        octet.length() > 1
                            && octet.charAt(0) == '0'
                    )
            ) {
                throw invalid(value);
            }

            int parsed = 0;

            for (
                int characterIndex = 0;
                characterIndex < octet.length();
                characterIndex++
            ) {
                char character =
                    octet.charAt(characterIndex);

                if (!Character.isDigit(character)) {
                    throw invalid(value);
                }

                parsed =
                    parsed * 10
                        + (
                            character
                                - '0'
                        );
            }

            if (parsed > 255) {
                throw invalid(value);
            }

            result[index] =
                (byte) parsed;
        }

        return result;
    }

    private static byte[] parseIpv6(String value) {
        if (
            value.indexOf('%') >= 0
                || !IPV6_LITERAL_CHARACTERS
                    .matcher(value)
                    .matches()
        ) {
            throw invalid(value);
        }

        try {
            InetAddress parsed =
                InetAddress.getByName(value);

            if (!(parsed instanceof Inet6Address)) {
                throw invalid(value);
            }

            return parsed.getAddress();
        }
        catch (UnknownHostException exception) {
            throw new IllegalArgumentException(
                "Invalid IP address literal: "
                    + value,
                exception
            );
        }
    }

    private static String format(byte[] value) {
        return value.length == Integer.BYTES
            ? formatIpv4(value)
            : formatIpv6(value);
    }

    private static String formatIpv4(byte[] value) {
        StringBuilder result =
            new StringBuilder();

        for (
            int index = 0;
            index < value.length;
            index++
        ) {
            if (index > 0) {
                result.append('.');
            }

            result.append(
                Byte.toUnsignedInt(
                    value[index]
                )
            );
        }

        return result.toString();
    }

    private static String formatIpv6(byte[] value) {
        int[] hextets =
            new int[8];

        for (
            int index = 0;
            index < hextets.length;
            index++
        ) {
            hextets[index] =
                (
                    Byte.toUnsignedInt(
                        value[index * 2]
                    ) << 8
                )
                    | Byte.toUnsignedInt(
                        value[index * 2 + 1]
                    );
        }

        int bestStart = -1;
        int bestLength = 0;
        int currentStart = -1;
        int currentLength = 0;

        for (
            int index = 0;
            index <= hextets.length;
            index++
        ) {
            boolean zero =
                index < hextets.length
                    && hextets[index] == 0;

            if (zero) {
                if (currentStart < 0) {
                    currentStart = index;
                }

                currentLength++;
                continue;
            }

            if (
                currentLength >= 2
                    && currentLength > bestLength
            ) {
                bestStart = currentStart;
                bestLength = currentLength;
            }

            currentStart = -1;
            currentLength = 0;
        }

        StringBuilder result =
            new StringBuilder();

        for (
            int index = 0;
            index < hextets.length;
            index++
        ) {
            if (index == bestStart) {
                result.append("::");
                index += bestLength - 1;
                continue;
            }

            if (
                result.length() > 0
                    && result.charAt(
                        result.length() - 1
                    ) != ':'
            ) {
                result.append(':');
            }

            result.append(
                Integer.toHexString(
                    hextets[index]
                )
            );
        }

        return result.toString();
    }

    private static IllegalArgumentException invalid(
        String value
    ) {
        return new IllegalArgumentException(
            "Invalid IP address literal: "
                + value
        );
    }

    @Override
    public boolean equals(Object candidate) {
        if (this == candidate) {
            return true;
        }

        if (!(candidate instanceof IpAddress other)) {
            return false;
        }

        return Arrays.equals(
            bytes,
            other.bytes
        );
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return value;
    }
}

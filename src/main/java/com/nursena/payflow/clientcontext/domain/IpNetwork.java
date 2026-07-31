package com.nursena.payflow.clientcontext.domain;

import java.util.Arrays;
import java.util.Objects;

public final class IpNetwork {

    private final IpAddress networkAddress;
    private final int prefixLength;
    private final String value;

    private IpNetwork(
        IpAddress networkAddress,
        int prefixLength
    ) {
        this.networkAddress =
            networkAddress;
        this.prefixLength =
            prefixLength;
        this.value =
            networkAddress.value()
                + "/"
                + prefixLength;
    }

    public static IpNetwork parse(String rawValue) {
        Objects.requireNonNull(
            rawValue,
            "IP network must not be null"
        );

        if (
            rawValue.isBlank()
                || !rawValue.equals(rawValue.trim())
        ) {
            throw invalid(rawValue);
        }

        int separator =
            rawValue.indexOf('/');

        if (
            separator <= 0
                || separator
                    != rawValue.lastIndexOf('/')
                || separator
                    == rawValue.length() - 1
        ) {
            throw invalid(rawValue);
        }

        IpAddress address =
            IpAddress.parse(
                rawValue.substring(
                    0,
                    separator
                )
            );

        String prefixValue =
            rawValue.substring(
                separator + 1
            );

        int prefixLength =
            parsePrefixLength(
                prefixValue,
                address.bitLength(),
                rawValue
            );

        byte[] original =
            address.bytes();

        byte[] network =
            mask(
                original,
                prefixLength
            );

        if (!Arrays.equals(original, network)) {
            throw new IllegalArgumentException(
                "IP network contains host bits: "
                    + rawValue
            );
        }

        return new IpNetwork(
            IpAddress.fromBytes(network),
            prefixLength
        );
    }

    public String value() {
        return value;
    }

    public IpAddress networkAddress() {
        return networkAddress;
    }

    public int prefixLength() {
        return prefixLength;
    }

    public boolean contains(IpAddress candidate) {
        Objects.requireNonNull(
            candidate,
            "candidate IP address must not be null"
        );

        if (
            candidate.bitLength()
                != networkAddress.bitLength()
        ) {
            return false;
        }

        byte[] candidateBytes =
            candidate.bytes();

        byte[] networkBytes =
            networkAddress.bytes();

        int completeBytes =
            prefixLength / Byte.SIZE;

        for (
            int index = 0;
            index < completeBytes;
            index++
        ) {
            if (
                candidateBytes[index]
                    != networkBytes[index]
            ) {
                return false;
            }
        }

        int remainingBits =
            prefixLength % Byte.SIZE;

        if (remainingBits == 0) {
            return true;
        }

        int mask =
            0xFF
                << (
                    Byte.SIZE
                        - remainingBits
                );

        return (
            Byte.toUnsignedInt(
                candidateBytes[completeBytes]
            ) & mask
        ) == (
            Byte.toUnsignedInt(
                networkBytes[completeBytes]
            ) & mask
        );
    }

    private static int parsePrefixLength(
        String value,
        int maximum,
        String networkValue
    ) {
        if (
            value.isEmpty()
                || value.length() > 3
        ) {
            throw invalid(networkValue);
        }

        int parsed = 0;

        for (
            int index = 0;
            index < value.length();
            index++
        ) {
            char character =
                value.charAt(index);

            if (!Character.isDigit(character)) {
                throw invalid(networkValue);
            }

            parsed =
                parsed * 10
                    + (
                        character
                            - '0'
                    );
        }

        if (parsed > maximum) {
            throw invalid(networkValue);
        }

        return parsed;
    }

    private static byte[] mask(
        byte[] source,
        int prefixLength
    ) {
        byte[] result =
            Arrays.copyOf(
                source,
                source.length
            );

        int completeBytes =
            prefixLength / Byte.SIZE;

        int remainingBits =
            prefixLength % Byte.SIZE;

        if (
            remainingBits > 0
                && completeBytes < result.length
        ) {
            int mask =
                0xFF
                    << (
                        Byte.SIZE
                            - remainingBits
                    );

            result[completeBytes] =
                (byte) (
                    Byte.toUnsignedInt(
                        result[completeBytes]
                    ) & mask
                );

            completeBytes++;
        }

        Arrays.fill(
            result,
            completeBytes,
            result.length,
            (byte) 0
        );

        return result;
    }

    private static IllegalArgumentException invalid(
        String value
    ) {
        return new IllegalArgumentException(
            "Invalid IP network: "
                + value
        );
    }

    @Override
    public boolean equals(Object candidate) {
        if (this == candidate) {
            return true;
        }

        if (!(candidate instanceof IpNetwork other)) {
            return false;
        }

        return prefixLength
            == other.prefixLength
            && networkAddress.equals(
                other.networkAddress
            );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            networkAddress,
            prefixLength
        );
    }

    @Override
    public String toString() {
        return value;
    }
}

package com.nursena.payflow.clientcontext.adapter.in.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.nursena.payflow.clientcontext.domain.IpAddress;

final class ForwardingHeaderParser {

    List<IpAddress> parseForwarded(
        String value,
        int maximumHops
    ) {
        List<String> elements =
            splitOutsideQuotes(
                value,
                ','
            );

        requireHopLimit(
            elements,
            maximumHops
        );

        List<IpAddress> result =
            new ArrayList<>();

        for (String element : elements) {
            result.add(
                parseForwardedElement(element)
            );
        }

        return List.copyOf(result);
    }

    List<IpAddress> parseXForwardedFor(
        String value,
        int maximumHops
    ) {
        List<String> elements =
            splitOutsideQuotes(
                value,
                ','
            );

        requireHopLimit(
            elements,
            maximumHops
        );

        List<IpAddress> result =
            new ArrayList<>();

        for (String element : elements) {
            String candidate =
                element.trim();

            if (
                candidate.isEmpty()
                    || candidate.indexOf('"') >= 0
            ) {
                throw malformed(
                    "invalid X-Forwarded-For element"
                );
            }

            result.add(
                parseNodeIdentifier(candidate)
            );
        }

        return List.copyOf(result);
    }

    private IpAddress parseForwardedElement(
        String element
    ) {
        List<String> parameters =
            splitOutsideQuotes(
                element,
                ';'
            );

        String forValue =
            null;

        for (String parameter : parameters) {
            String candidate =
                parameter.trim();

            int separator =
                candidate.indexOf('=');

            if (
                separator <= 0
                    || separator
                        == candidate.length() - 1
            ) {
                throw malformed(
                    "invalid Forwarded parameter"
                );
            }

            String name =
                candidate
                    .substring(
                        0,
                        separator
                    )
                    .trim()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if (!name.equals("for")) {
                continue;
            }

            if (forValue != null) {
                throw malformed(
                    "duplicate Forwarded for parameter"
                );
            }

            forValue =
                candidate
                    .substring(
                        separator + 1
                    )
                    .trim();
        }

        if (forValue == null) {
            throw malformed(
                "Forwarded element has no for parameter"
            );
        }

        return parseNodeIdentifier(
            unquote(forValue)
        );
    }

    private IpAddress parseNodeIdentifier(
        String value
    ) {
        if (
            value.isEmpty()
                || value.equalsIgnoreCase(
                    "unknown"
                )
                || value.startsWith("_")
        ) {
            throw malformed(
                "unsupported forwarding node identifier"
            );
        }

        if (value.startsWith("[")) {
            int closingBracket =
                value.indexOf(']');

            if (closingBracket <= 1) {
                throw malformed(
                    "invalid bracketed IPv6 address"
                );
            }

            String addressValue =
                value.substring(
                    1,
                    closingBracket
                );

            String suffix =
                value.substring(
                    closingBracket + 1
                );

            if (!suffix.isEmpty()) {
                if (!suffix.startsWith(":")) {
                    throw malformed(
                        "invalid bracketed address suffix"
                    );
                }

                validatePort(
                    suffix.substring(1)
                );
            }

            IpAddress address =
                IpAddress.parse(addressValue);

            if (!address.isIpv6()) {
                throw malformed(
                    "brackets require an IPv6 address"
                );
            }

            return address;
        }

        try {
            return IpAddress.parse(value);
        }
        catch (IllegalArgumentException exception) {
            int separator =
                value.lastIndexOf(':');

            boolean ipv4WithPort =
                separator > 0
                    && value.indexOf(':')
                        == separator
                    && value
                        .substring(
                            0,
                            separator
                        )
                        .indexOf('.') >= 0;

            if (!ipv4WithPort) {
                throw malformed(
                    "invalid forwarding address"
                );
            }

            validatePort(
                value.substring(
                    separator + 1
                )
            );

            return IpAddress.parse(
                value.substring(
                    0,
                    separator
                )
            );
        }
    }

    private static void validatePort(
        String value
    ) {
        if (
            value.isEmpty()
                || value.length() > 5
        ) {
            throw malformed(
                "invalid forwarding port"
            );
        }

        int port = 0;

        for (
            int index = 0;
            index < value.length();
            index++
        ) {
            char character =
                value.charAt(index);

            if (!Character.isDigit(character)) {
                throw malformed(
                    "invalid forwarding port"
                );
            }

            port =
                port * 10
                    + (
                        character
                            - '0'
                    );
        }

        if (
            port < 1
                || port > 65_535
        ) {
            throw malformed(
                "invalid forwarding port"
            );
        }
    }

    private static String unquote(
        String value
    ) {
        if (!value.startsWith("\"")) {
            if (value.indexOf('"') >= 0) {
                throw malformed(
                    "invalid quoted forwarding value"
                );
            }

            return value;
        }

        if (
            value.length() < 2
                || !value.endsWith("\"")
        ) {
            throw malformed(
                "unterminated quoted forwarding value"
            );
        }

        String inner =
            value.substring(
                1,
                value.length() - 1
            );

        StringBuilder result =
            new StringBuilder();

        boolean escaped =
            false;

        for (
            int index = 0;
            index < inner.length();
            index++
        ) {
            char character =
                inner.charAt(index);

            if (escaped) {
                if (
                    character != '\\'
                        && character != '"'
                ) {
                    throw malformed(
                        "unsupported quoted escape"
                    );
                }

                result.append(character);
                escaped = false;
                continue;
            }

            if (character == '\\') {
                escaped = true;
                continue;
            }

            if (
                character == '"'
                    || Character.isISOControl(
                        character
                    )
            ) {
                throw malformed(
                    "invalid quoted forwarding value"
                );
            }

            result.append(character);
        }

        if (escaped) {
            throw malformed(
                "unterminated quoted escape"
            );
        }

        return result.toString();
    }

    private static List<String> splitOutsideQuotes(
        String value,
        char delimiter
    ) {
        if (value == null) {
            throw malformed(
                "forwarding header must not be null"
            );
        }

        List<String> result =
            new ArrayList<>();

        StringBuilder current =
            new StringBuilder();

        boolean quoted =
            false;

        boolean escaped =
            false;

        for (
            int index = 0;
            index < value.length();
            index++
        ) {
            char character =
                value.charAt(index);

            if (escaped) {
                current.append(character);
                escaped = false;
                continue;
            }

            if (
                quoted
                    && character == '\\'
            ) {
                current.append(character);
                escaped = true;
                continue;
            }

            if (character == '"') {
                quoted = !quoted;
                current.append(character);
                continue;
            }

            if (
                character == delimiter
                    && !quoted
            ) {
                addElement(
                    result,
                    current
                );

                current =
                    new StringBuilder();

                continue;
            }

            current.append(character);
        }

        if (
            quoted
                || escaped
        ) {
            throw malformed(
                "unterminated quoted forwarding value"
            );
        }

        addElement(
            result,
            current
        );

        return result;
    }

    private static void addElement(
        List<String> result,
        StringBuilder current
    ) {
        String element =
            current
                .toString()
                .trim();

        if (element.isEmpty()) {
            throw malformed(
                "empty forwarding element"
            );
        }

        result.add(element);
    }

    private static void requireHopLimit(
        List<String> elements,
        int maximumHops
    ) {
        if (
            elements.size()
                > maximumHops
        ) {
            throw new ExcessiveForwardedHopsException(
                elements.size(),
                maximumHops
            );
        }
    }

    private static IllegalArgumentException malformed(
        String message
    ) {
        return new IllegalArgumentException(message);
    }

    static final class ExcessiveForwardedHopsException
        extends IllegalArgumentException {

        ExcessiveForwardedHopsException(
            int actual,
            int maximum
        ) {
            super(
                "forwarding hop count "
                    + actual
                    + " exceeds "
                    + maximum
            );
        }
    }
}

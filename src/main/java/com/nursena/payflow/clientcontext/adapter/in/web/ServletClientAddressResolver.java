package com.nursena.payflow.clientcontext.adapter.in.web;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;

import com.nursena.payflow.clientcontext.domain.ClientAddressResolutionOutcome;
import com.nursena.payflow.clientcontext.domain.ClientAddressSource;
import com.nursena.payflow.clientcontext.domain.IpAddress;
import com.nursena.payflow.clientcontext.domain.ResolvedClientAddress;
import com.nursena.payflow.clientcontext.domain.TrustedProxyChainResolver;

import jakarta.servlet.http.HttpServletRequest;

public final class ServletClientAddressResolver {

    private static final String FORWARDED_HEADER =
        "Forwarded";

    private static final String X_FORWARDED_FOR_HEADER =
        "X-Forwarded-For";

    private final TrustedProxyChainResolver chainResolver;
    private final ForwardingHeaderParser headerParser;
    private final int maximumHeaderLength;
    private final int maximumHops;

    public ServletClientAddressResolver(
        TrustedProxyProperties properties
    ) {
        Objects.requireNonNull(
            properties,
            "trusted proxy properties must not be null"
        );

        this.chainResolver =
            new TrustedProxyChainResolver(
                properties.trustedProxyNetworks()
            );

        this.headerParser =
            new ForwardingHeaderParser();

        this.maximumHeaderLength =
            properties.maxForwardedHeaderLength();

        this.maximumHops =
            properties.maxForwardedHops();
    }

    public ResolvedClientAddress resolve(
        HttpServletRequest request
    ) {
        Objects.requireNonNull(
            request,
            "HTTP request must not be null"
        );

        IpAddress directPeer =
            IpAddress.parse(
                request.getRemoteAddr()
            );

        if (!chainResolver.isTrusted(directPeer)) {
            return direct(
                directPeer,
                ClientAddressResolutionOutcome
                    .UNTRUSTED_PEER
            );
        }

        CollectedHeader forwarded =
            collectHeader(
                request,
                FORWARDED_HEADER
            );

        CollectedHeader selected =
            forwarded.present()
                ? forwarded
                : collectHeader(
                    request,
                    X_FORWARDED_FOR_HEADER
                );

        if (!selected.present()) {
            return direct(
                directPeer,
                ClientAddressResolutionOutcome
                    .MISSING_HEADER
            );
        }

        if (selected.oversized()) {
            return fallback(
                directPeer,
                selected.source(),
                ClientAddressResolutionOutcome
                    .OVERSIZED_HEADER
            );
        }

        try {
            List<IpAddress> chain =
                selected.source()
                    == ClientAddressSource.FORWARDED
                    ? headerParser.parseForwarded(
                        selected.value(),
                        maximumHops
                    )
                    : headerParser.parseXForwardedFor(
                        selected.value(),
                        maximumHops
                    );

            IpAddress effectiveAddress =
                chainResolver.resolve(
                    directPeer,
                    chain
                );

            return new ResolvedClientAddress(
                effectiveAddress,
                selected.source(),
                ClientAddressResolutionOutcome
                    .RESOLVED
            );
        }
        catch (
            ForwardingHeaderParser
                .ExcessiveForwardedHopsException
                exception
        ) {
            return fallback(
                directPeer,
                selected.source(),
                ClientAddressResolutionOutcome
                    .EXCESSIVE_HOPS
            );
        }
        catch (IllegalArgumentException exception) {
            return fallback(
                directPeer,
                selected.source(),
                ClientAddressResolutionOutcome
                    .MALFORMED_HEADER
            );
        }
    }

    private CollectedHeader collectHeader(
        HttpServletRequest request,
        String headerName
    ) {
        Enumeration<String> values =
            request.getHeaders(headerName);

        if (
            values == null
                || !values.hasMoreElements()
        ) {
            return CollectedHeader.missing();
        }

        List<String> collected =
            new ArrayList<>();

        int totalLength =
            0;

        while (values.hasMoreElements()) {
            String value =
                values.nextElement();

            String normalized =
                value == null
                    ? ""
                    : value;

            if (!collected.isEmpty()) {
                totalLength++;
            }

            totalLength +=
                normalized.length();

            if (
                totalLength
                    > maximumHeaderLength
            ) {
                return CollectedHeader.oversized(
                    sourceFor(headerName)
                );
            }

            collected.add(normalized);
        }

        return new CollectedHeader(
            true,
            false,
            String.join(
                ",",
                collected
            ),
            sourceFor(headerName)
        );
    }

    private static ClientAddressSource sourceFor(
        String headerName
    ) {
        return headerName.equalsIgnoreCase(
            FORWARDED_HEADER
        )
            ? ClientAddressSource.FORWARDED
            : ClientAddressSource
                .X_FORWARDED_FOR;
    }

    private static ResolvedClientAddress direct(
        IpAddress directPeer,
        ClientAddressResolutionOutcome outcome
    ) {
        return new ResolvedClientAddress(
            directPeer,
            ClientAddressSource.DIRECT_PEER,
            outcome
        );
    }

    private static ResolvedClientAddress fallback(
        IpAddress directPeer,
        ClientAddressSource source,
        ClientAddressResolutionOutcome outcome
    ) {
        return new ResolvedClientAddress(
            directPeer,
            source,
            outcome
        );
    }

    private record CollectedHeader(
        boolean present,
        boolean oversized,
        String value,
        ClientAddressSource source
    ) {

        private static CollectedHeader missing() {
            return new CollectedHeader(
                false,
                false,
                "",
                ClientAddressSource.DIRECT_PEER
            );
        }

        private static CollectedHeader oversized(
            ClientAddressSource source
        ) {
            return new CollectedHeader(
                true,
                true,
                "",
                source
            );
        }
    }
}

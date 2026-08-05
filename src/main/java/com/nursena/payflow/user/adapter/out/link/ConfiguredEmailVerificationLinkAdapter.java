package com.nursena.payflow.user.adapter.out.link;

import java.net.URI;
import java.util.Objects;

import com.nursena.payflow.user.application.port.out
    .EmailVerificationLinkPort;
import com.nursena.payflow.user.domain.exception
    .InvalidAccountActionCredentialException;
import org.springframework.web.util.UriComponentsBuilder;

final class ConfiguredEmailVerificationLinkAdapter
    implements EmailVerificationLinkPort {

    private static final String CREDENTIAL_PARAMETER =
        "token";

    private final URI confirmationUri;

    ConfiguredEmailVerificationLinkAdapter(
        URI confirmationUri
    ) {
        this.confirmationUri = Objects.requireNonNull(
            confirmationUri,
            "confirmationUri must not be null"
        );
    }

    @Override
    public URI build(String credential) {
        if (credential == null || credential.isBlank()) {
            throw new
                InvalidAccountActionCredentialException();
        }

        return UriComponentsBuilder
            .fromUri(confirmationUri)
            .queryParam(
                CREDENTIAL_PARAMETER,
                credential
            )
            .build()
            .encode()
            .toUri();
    }
}

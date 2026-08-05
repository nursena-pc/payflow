package com.nursena.payflow.user.adapter.out.link;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

import org.springframework.boot.context.properties
    .ConfigurationProperties;

@ConfigurationProperties(
    prefix = "payflow.security.account-action"
)
public record EmailVerificationLinkProperties(
    URI emailVerificationConfirmationUri
) {

    public EmailVerificationLinkProperties {
        URI checkedUri = Objects.requireNonNull(
            emailVerificationConfirmationUri,
            "emailVerificationConfirmationUri "
                + "must not be null"
        );

        validate(checkedUri);
    }

    private static void validate(URI uri) {
        String scheme = uri.getScheme();

        if (
            !uri.isAbsolute()
                || scheme == null
                || !isHttpScheme(scheme)
                || uri.getHost() == null
                || uri.getHost().isBlank()
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
        ) {
            throw new IllegalArgumentException(
                "emailVerificationConfirmationUri must be "
                    + "an absolute HTTP(S) URI without "
                    + "userinfo, query, or fragment"
            );
        }
    }

    private static boolean isHttpScheme(String scheme) {
        String normalized = scheme.toLowerCase(
            Locale.ROOT
        );

        return "http".equals(normalized)
            || "https".equals(normalized);
    }
}

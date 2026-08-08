package com.nursena.payflow.user.adapter.out.security;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.nursena.payflow.user.application.port.out.TotpProvisioningUriPort;
import com.nursena.payflow.user.domain.model.EmailAddress;

final class ConfiguredTotpProvisioningUriAdapter
    implements TotpProvisioningUriPort {

    private final String issuer;

    ConfiguredTotpProvisioningUriAdapter(String issuer) {
        this.issuer = Objects.requireNonNull(
            issuer,
            "issuer must not be null"
        );
    }

    @Override
    public String build(
        EmailAddress account,
        String base32Secret
    ) {
        EmailAddress checkedAccount = Objects.requireNonNull(
            account,
            "account must not be null"
        );
        String checkedSecret = Objects.requireNonNull(
            base32Secret,
            "base32Secret must not be null"
        );

        if (!checkedSecret.matches("[A-Z2-7]{32}")) {
            throw new IllegalArgumentException(
                "base32Secret must be canonical Base32 without padding"
            );
        }

        String label = encode(issuer)
            + ":"
            + encode(checkedAccount.value());

        return "otpauth://totp/"
            + label
            + "?secret="
            + checkedSecret
            + "&issuer="
            + encode(issuer)
            + "&algorithm=SHA1&digits=6&period=30";
    }

    private static String encode(String value) {
        return URLEncoder.encode(
            value,
            StandardCharsets.UTF_8
        ).replace("+", "%20");
    }
}

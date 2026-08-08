package com.nursena.payflow.user.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nursena.payflow.user.domain.model.EmailAddress;
import org.junit.jupiter.api.Test;

class ConfiguredTotpProvisioningUriAdapterTest {

    private static final String SECRET = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";

    @Test
    void shouldBuildStandardsCompatibleProvisioningUri() {
        String uri = new ConfiguredTotpProvisioningUriAdapter("PayFlow")
            .build(EmailAddress.of("NURSENA@example.com"), SECRET);
        assertThat(uri).isEqualTo(
            "otpauth://totp/PayFlow:nursena%40example.com?secret="
                + SECRET
                + "&issuer=PayFlow&algorithm=SHA1&digits=6&period=30"
        );
    }

    @Test
    void shouldPercentEncodeIssuerAndAccountLabel() {
        String uri = new ConfiguredTotpProvisioningUriAdapter("Pay Flow")
            .build(EmailAddress.of("user+tag@example.com"), SECRET);
        assertThat(uri).contains("Pay%20Flow:user%2Btag%40example.com");
    }

    @Test
    void shouldRejectNonCanonicalSecret() {
        assertThatThrownBy(() -> new ConfiguredTotpProvisioningUriAdapter("PayFlow")
            .build(EmailAddress.of("user@example.com"), "abc"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}

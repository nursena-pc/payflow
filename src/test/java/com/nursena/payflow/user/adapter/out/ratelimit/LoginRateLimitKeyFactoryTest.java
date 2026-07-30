package com.nursena.payflow.user.adapter.out.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.nursena.payflow.user.domain.model.EmailAddress;
import org.junit.jupiter.api.Test;

class LoginRateLimitKeyFactoryTest {

    private static final String HEX_DIGEST_PATTERN =
        "[0-9a-f]{64}";

    @Test
    void shouldBuildDeterministicNormalizedIdentityKey() {
        String firstKey =
            LoginRateLimitKeyFactory.identityKey(
                EmailAddress.of(
                    "  NURSENA@EXAMPLE.COM  "
                )
            );

        String secondKey =
            LoginRateLimitKeyFactory.identityKey(
                EmailAddress.of(
                    "nursena@example.com"
                )
            );

        assertThat(firstKey)
            .isEqualTo(secondKey)
            .startsWith(
                "payflow:security:login:"
                    + "identity:"
            )
            .doesNotContain(
                "nursena@example.com"
            );

        assertThat(
            firstKey.substring(
                firstKey.lastIndexOf(':') + 1
            )
        )
            .matches(
                HEX_DIGEST_PATTERN
            );
    }

    @Test
    void shouldBuildNormalizedClientKey() {
        String firstKey =
            LoginRateLimitKeyFactory.clientKey(
                " 2001:DB8::1 "
            );

        String secondKey =
            LoginRateLimitKeyFactory.clientKey(
                "2001:db8::1"
            );

        assertThat(firstKey)
            .isEqualTo(secondKey)
            .startsWith(
                "payflow:security:login:"
                    + "client:"
            )
            .doesNotContain(
                "2001:db8::1"
            );

        assertThat(
            firstKey.substring(
                firstKey.lastIndexOf(':') + 1
            )
        )
            .matches(
                HEX_DIGEST_PATTERN
            );
    }
}

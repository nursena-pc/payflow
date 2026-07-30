package com.nursena.payflow.user.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nursena.payflow.user.domain.model.EmailAddress;
import org.junit.jupiter.api.Test;

class LoginRateLimitRequestTest {

    @Test
    void shouldRetainIdentityAndClientAddress() {
        EmailAddress identity =
            EmailAddress.of(
                "nursena@example.com"
            );

        LoginRateLimitRequest request =
            new LoginRateLimitRequest(
                identity,
                "127.0.0.1"
            );

        assertThat(request.identity())
            .isEqualTo(identity);

        assertThat(request.clientAddress())
            .isEqualTo("127.0.0.1");
    }

    @Test
    void shouldRequireIdentity() {
        assertThatThrownBy(() ->
            new LoginRateLimitRequest(
                null,
                "127.0.0.1"
            )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "identity must not be null"
            );
    }

    @Test
    void shouldRejectBlankClientAddress() {
        assertThatThrownBy(() ->
            new LoginRateLimitRequest(
                EmailAddress.of(
                    "nursena@example.com"
                ),
                "  "
            )
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessage(
                "clientAddress must not be blank"
            );
    }
}

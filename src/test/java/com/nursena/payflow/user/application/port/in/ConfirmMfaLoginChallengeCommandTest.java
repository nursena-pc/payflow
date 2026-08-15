package com.nursena.payflow.user.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nursena.payflow.clientcontext.domain.IpAddress;
import org.junit.jupiter.api.Test;

class ConfirmMfaLoginChallengeCommandTest {

    private static final IpAddress CLIENT_ADDRESS =
        IpAddress.parse("203.0.113.10");

    @Test
    void shouldKeepValuesForApplicationValidation() {
        ConfirmMfaLoginChallengeCommand command =
            new ConfirmMfaLoginChallengeCommand(
                "challenge", "123456", CLIENT_ADDRESS
            );
        assertThat(command.challengeToken()).isEqualTo("challenge");
        assertThat(command.code()).isEqualTo("123456");
        assertThat(command.effectiveClientAddress())
            .isEqualTo(CLIENT_ADDRESS);
    }

    @Test
    void shouldRedactChallengeAndCode() {
        ConfirmMfaLoginChallengeCommand command =
            new ConfirmMfaLoginChallengeCommand(
                "challenge", "123456", CLIENT_ADDRESS
            );
        assertThat(command.toString())
            .isEqualTo("ConfirmMfaLoginChallengeCommand[redacted]")
            .doesNotContain("challenge", "123456");
    }

    @Test
    void shouldAllowNullForStableApplicationFailureContract() {
        ConfirmMfaLoginChallengeCommand command =
            new ConfirmMfaLoginChallengeCommand(null, null, CLIENT_ADDRESS);
        assertThat(command.challengeToken()).isNull();
        assertThat(command.code()).isNull();
    }

    @Test
    void shouldRejectMissingEffectiveClientAddress() {
        assertThatThrownBy(() ->
            new ConfirmMfaLoginChallengeCommand(
                "challenge", "123456", null
            )
        ).isInstanceOf(NullPointerException.class);
    }
}

package com.nursena.payflow.user.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class ConfirmMfaLoginChallengeCommandTest {

    @Test
    void shouldKeepValuesForApplicationValidation() {
        ConfirmMfaLoginChallengeCommand command =
            new ConfirmMfaLoginChallengeCommand("challenge", "123456");
        assertThat(command.challengeToken()).isEqualTo("challenge");
        assertThat(command.code()).isEqualTo("123456");
    }

    @Test
    void shouldRedactChallengeAndCode() {
        ConfirmMfaLoginChallengeCommand command =
            new ConfirmMfaLoginChallengeCommand("challenge", "123456");
        assertThat(command.toString())
            .isEqualTo("ConfirmMfaLoginChallengeCommand[redacted]")
            .doesNotContain("challenge", "123456");
    }

    @Test
    void shouldAllowNullForStableApplicationFailureContract() {
        ConfirmMfaLoginChallengeCommand command =
            new ConfirmMfaLoginChallengeCommand(null, null);
        assertThat(command.challengeToken()).isNull();
        assertThat(command.code()).isNull();
    }
}

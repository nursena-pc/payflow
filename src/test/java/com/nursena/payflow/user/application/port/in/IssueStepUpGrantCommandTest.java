package com.nursena.payflow.user.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import com.nursena.payflow.clientcontext.domain.IpAddress;
import org.junit.jupiter.api.Test;

class IssueStepUpGrantCommandTest {

    private static final UUID SUBJECT_ID =
        UUID.fromString("10000000-0000-0000-0000-000000000105");
    private static final IpAddress CLIENT_ADDRESS =
        IpAddress.parse("203.0.113.10");

    @Test
    void shouldRetainTrustedApplicationInputs() {
        IssueStepUpGrantCommand command = command();

        assertThat(command.subjectId()).isEqualTo(SUBJECT_ID);
        assertThat(command.purpose()).isEqualTo("mfa-disable");
        assertThat(command.code()).isEqualTo("123456");
        assertThat(command.effectiveClientAddress())
            .isEqualTo(CLIENT_ADDRESS);
    }

    @Test
    void shouldRedactSubjectPurposeProofAndClientAddress() {
        IssueStepUpGrantCommand command = command();

        assertThat(command.toString())
            .isEqualTo("IssueStepUpGrantCommand[redacted]")
            .doesNotContain(
                SUBJECT_ID.toString(),
                "mfa-disable",
                "123456",
                "203.0.113.10"
            );
    }

    @Test
    void shouldRejectMissingTrustedIdentityInputs() {
        assertThatThrownBy(() ->
            new IssueStepUpGrantCommand(
                null,
                "mfa-disable",
                "123456",
                CLIENT_ADDRESS
            )
        ).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() ->
            new IssueStepUpGrantCommand(
                SUBJECT_ID,
                "mfa-disable",
                "123456",
                null
            )
        ).isInstanceOf(NullPointerException.class);
    }

    private static IssueStepUpGrantCommand command() {
        return new IssueStepUpGrantCommand(
            SUBJECT_ID,
            "mfa-disable",
            "123456",
            CLIENT_ADDRESS
        );
    }
}

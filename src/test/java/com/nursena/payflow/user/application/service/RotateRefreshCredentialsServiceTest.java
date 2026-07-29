package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;

import com.nursena.payflow.user.application.port.in.RotateRefreshCredentialsCommand;
import com.nursena.payflow.user.application.port.in.RotateRefreshCredentialsResult;
import com.nursena.payflow.user.application.port.out.RefreshTokenDigestPort;
import com.nursena.payflow.user.domain.exception.InvalidRefreshTokenException;
import com.nursena.payflow.user.domain.model.RefreshTokenDigest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RotateRefreshCredentialsServiceTest {

    private static final String CURRENT_TOKEN =
        "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA";

    private static final RefreshTokenDigest
        CURRENT_DIGEST =
        digest((byte) 7);

    private static final RotateRefreshCredentialsResult
        SUCCESS_RESULT =
        new RotateRefreshCredentialsResult(
            "rotated.jwt.token",
            Instant.parse(
                "2026-07-28T12:15:00Z"
            ),
            "ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8",
            Instant.parse(
                "2026-08-04T12:00:00Z"
            )
        );

    @Mock
    private RefreshTokenDigestPort
        refreshTokenDigest;

    @Mock
    private RotateRefreshCredentialsTransaction
        rotationTransaction;

    private RotateRefreshCredentialsService service;

    @BeforeEach
    void setUp() {
        service =
            new RotateRefreshCredentialsService(
                refreshTokenDigest,
                rotationTransaction
            );
    }

    @Test
    void shouldReturnCommittedSuccessfulRotation() {
        when(refreshTokenDigest.digest(
            CURRENT_TOKEN
        ))
            .thenReturn(
                CURRENT_DIGEST
            );

        when(rotationTransaction.rotate(
            CURRENT_DIGEST
        ))
            .thenReturn(
                new RotateRefreshCredentialsOutcome
                    .Succeeded(
                        SUCCESS_RESULT
                    )
            );

        RotateRefreshCredentialsResult result =
            service.rotate(
                command()
            );

        assertThat(result)
            .isSameAs(
                SUCCESS_RESULT
            );

        verify(refreshTokenDigest)
            .digest(CURRENT_TOKEN);

        verify(rotationTransaction)
            .rotate(CURRENT_DIGEST);
    }

    @Test
    void shouldRaisePublicFailureAfterRejectedTransaction() {
        when(refreshTokenDigest.digest(
            CURRENT_TOKEN
        ))
            .thenReturn(
                CURRENT_DIGEST
            );

        when(rotationTransaction.rotate(
            CURRENT_DIGEST
        ))
            .thenReturn(
                RotateRefreshCredentialsOutcome
                    .Rejected
                    .INSTANCE
            );

        assertThatThrownBy(() ->
            service.rotate(
                command()
            )
        )
            .isInstanceOf(
                InvalidRefreshTokenException.class
            )
            .hasMessage(
                "Refresh token is invalid."
            );

        verify(rotationTransaction)
            .rotate(CURRENT_DIGEST);
    }

    @Test
    void shouldRejectMalformedCredentialBeforeTransaction() {
        InvalidRefreshTokenException failure =
            new InvalidRefreshTokenException();

        when(refreshTokenDigest.digest(
            CURRENT_TOKEN
        ))
            .thenThrow(
                failure
            );

        assertThatThrownBy(() ->
            service.rotate(
                command()
            )
        )
            .isSameAs(
                failure
            );

        verifyNoInteractions(
            rotationTransaction
        );
    }

    @Test
    void shouldRequireCommand() {
        assertThatThrownBy(() ->
            service.rotate(null)
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "command must not be null"
            );

        verifyNoInteractions(
            refreshTokenDigest,
            rotationTransaction
        );
    }

    private static RotateRefreshCredentialsCommand
    command() {
        return new RotateRefreshCredentialsCommand(
            CURRENT_TOKEN
        );
    }

    private static RefreshTokenDigest digest(
        byte fillValue
    ) {
        byte[] bytes =
            new byte[
                RefreshTokenDigest
                    .SHA_256_LENGTH_BYTES
            ];

        Arrays.fill(
            bytes,
            fillValue
        );

        return RefreshTokenDigest.of(
            bytes
        );
    }
}

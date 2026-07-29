package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import com.nursena.payflow.user.application.port.in.RevokeCurrentRefreshSessionCommand;
import com.nursena.payflow.user.application.port.out.RefreshTokenDigestPort;
import com.nursena.payflow.user.domain.exception.InvalidRefreshTokenException;
import com.nursena.payflow.user.domain.model.RefreshTokenDigest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RevokeCurrentRefreshSessionServiceTest {

    private static final String REFRESH_TOKEN =
        "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA";

    private static final RefreshTokenDigest DIGEST =
        digest((byte) 7);

    @Mock
    private RefreshTokenDigestPort
        refreshTokenDigest;

    @Mock
    private RevokeCurrentRefreshSessionTransaction
        revocationTransaction;

    private RevokeCurrentRefreshSessionService
        service;

    @BeforeEach
    void setUp() {
        service =
            new RevokeCurrentRefreshSessionService(
                refreshTokenDigest,
                revocationTransaction
            );
    }

    @Test
    void shouldRevokeCurrentRefreshSession() {
        when(refreshTokenDigest.digest(
            REFRESH_TOKEN
        ))
            .thenReturn(
                DIGEST
            );

        service.revoke(
            command()
        );

        verify(refreshTokenDigest)
            .digest(
                REFRESH_TOKEN
            );

        verify(revocationTransaction)
            .revoke(
                DIGEST
            );
    }

    @Test
    void shouldCompleteWhenRefreshTokenIsMalformed() {
        when(refreshTokenDigest.digest(
            REFRESH_TOKEN
        ))
            .thenThrow(
                new InvalidRefreshTokenException()
            );

        service.revoke(
            command()
        );

        verify(refreshTokenDigest)
            .digest(
                REFRESH_TOKEN
            );

        verifyNoInteractions(
            revocationTransaction
        );
    }

    @Test
    void shouldPropagateRevocationInfrastructureFailure() {
        when(refreshTokenDigest.digest(
            REFRESH_TOKEN
        ))
            .thenReturn(
                DIGEST
            );

        doThrow(
            new IllegalStateException(
                "revocation persistence failed"
            )
        )
            .when(revocationTransaction)
            .revoke(
                DIGEST
            );

        assertThatThrownBy(() ->
            service.revoke(
                command()
            )
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessage(
                "revocation persistence failed"
            );
    }

    @Test
    void shouldRequireCommand() {
        assertThatThrownBy(() ->
            service.revoke(
                null
            )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "command must not be null"
            );

        verifyNoInteractions(
            refreshTokenDigest,
            revocationTransaction
        );
    }

    private static RevokeCurrentRefreshSessionCommand
    command() {
        return new RevokeCurrentRefreshSessionCommand(
            REFRESH_TOKEN
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

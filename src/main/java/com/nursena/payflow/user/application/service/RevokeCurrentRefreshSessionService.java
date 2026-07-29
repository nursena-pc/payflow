package com.nursena.payflow.user.application.service;

import java.util.Objects;

import com.nursena.payflow.user.application.port.in.RevokeCurrentRefreshSessionCommand;
import com.nursena.payflow.user.application.port.in.RevokeCurrentRefreshSessionUseCase;
import com.nursena.payflow.user.application.port.out.RefreshTokenDigestPort;
import com.nursena.payflow.user.domain.exception.InvalidRefreshTokenException;
import com.nursena.payflow.user.domain.model.RefreshTokenDigest;
import org.springframework.stereotype.Service;

@Service
public class RevokeCurrentRefreshSessionService
    implements RevokeCurrentRefreshSessionUseCase {

    private final RefreshTokenDigestPort
        refreshTokenDigest;

    private final RevokeCurrentRefreshSessionTransaction
        revocationTransaction;

    public RevokeCurrentRefreshSessionService(
        RefreshTokenDigestPort refreshTokenDigest,
        RevokeCurrentRefreshSessionTransaction
            revocationTransaction
    ) {
        this.refreshTokenDigest =
            Objects.requireNonNull(
                refreshTokenDigest,
                "refreshTokenDigest must not be null"
            );

        this.revocationTransaction =
            Objects.requireNonNull(
                revocationTransaction,
                "revocationTransaction must not be null"
            );
    }

    @Override
    public void revoke(
        RevokeCurrentRefreshSessionCommand command
    ) {
        RevokeCurrentRefreshSessionCommand
            checkedCommand =
            Objects.requireNonNull(
                command,
                "command must not be null"
            );

        RefreshTokenDigest digest;

        try {
            digest =
                refreshTokenDigest.digest(
                    checkedCommand.refreshToken()
                );
        } catch (
            InvalidRefreshTokenException ignored
        ) {
            return;
        }

        revocationTransaction.revoke(
            digest
        );
    }
}

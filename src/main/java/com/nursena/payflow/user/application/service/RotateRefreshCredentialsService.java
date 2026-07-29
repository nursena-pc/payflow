package com.nursena.payflow.user.application.service;

import java.util.Objects;

import com.nursena.payflow.user.application.port.in.RotateRefreshCredentialsCommand;
import com.nursena.payflow.user.application.port.in.RotateRefreshCredentialsResult;
import com.nursena.payflow.user.application.port.in.RotateRefreshCredentialsUseCase;
import com.nursena.payflow.user.application.port.out.RefreshTokenDigestPort;
import com.nursena.payflow.user.domain.exception.InvalidRefreshTokenException;
import com.nursena.payflow.user.domain.model.RefreshTokenDigest;
import org.springframework.stereotype.Service;

@Service
public class RotateRefreshCredentialsService
    implements RotateRefreshCredentialsUseCase {

    private final RefreshTokenDigestPort
        refreshTokenDigest;

    private final RotateRefreshCredentialsTransaction
        rotationTransaction;

    public RotateRefreshCredentialsService(
        RefreshTokenDigestPort refreshTokenDigest,
        RotateRefreshCredentialsTransaction
            rotationTransaction
    ) {
        this.refreshTokenDigest =
            Objects.requireNonNull(
                refreshTokenDigest,
                "refreshTokenDigest must not be null"
            );

        this.rotationTransaction =
            Objects.requireNonNull(
                rotationTransaction,
                "rotationTransaction must not be null"
            );
    }

    @Override
    public RotateRefreshCredentialsResult rotate(
        RotateRefreshCredentialsCommand command
    ) {
        RotateRefreshCredentialsCommand checkedCommand =
            Objects.requireNonNull(
                command,
                "command must not be null"
            );

        RefreshTokenDigest currentDigest =
            refreshTokenDigest.digest(
                checkedCommand.refreshToken()
            );

        RotateRefreshCredentialsOutcome outcome =
            rotationTransaction.rotate(
                currentDigest
            );

        if (
            outcome instanceof
                RotateRefreshCredentialsOutcome
                    .Succeeded succeeded
        ) {
            return succeeded.result();
        }

        throw new InvalidRefreshTokenException();
    }
}

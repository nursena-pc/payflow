package com.nursena.payflow.user.application.port.in;

import java.util.Objects;

public record RevokeCurrentRefreshSessionCommand(
    String refreshToken
) {

    public RevokeCurrentRefreshSessionCommand {
        Objects.requireNonNull(
            refreshToken,
            "refreshToken must not be null"
        );

        if (refreshToken.isBlank()) {
            throw new IllegalArgumentException(
                "refreshToken must not be blank"
            );
        }
    }

    @Override
    public String toString() {
        return "RevokeCurrentRefreshSessionCommand[redacted]";
    }
}

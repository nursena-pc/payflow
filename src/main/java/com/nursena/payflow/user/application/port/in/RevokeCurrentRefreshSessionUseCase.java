package com.nursena.payflow.user.application.port.in;

public interface RevokeCurrentRefreshSessionUseCase {

    void revoke(
        RevokeCurrentRefreshSessionCommand command
    );
}

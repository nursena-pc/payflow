package com.nursena.payflow.user.application.port.in;

public interface BeginMfaEnrollmentUseCase {

    BeginMfaEnrollmentResult begin(
        BeginMfaEnrollmentCommand command
    );
}

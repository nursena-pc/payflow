package com.nursena.payflow.user.application.port.in;

public interface ConfirmMfaEnrollmentUseCase {

    ConfirmMfaEnrollmentResult confirm(
        ConfirmMfaEnrollmentCommand command
    );
}

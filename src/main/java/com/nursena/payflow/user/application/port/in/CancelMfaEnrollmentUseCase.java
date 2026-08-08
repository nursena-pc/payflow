package com.nursena.payflow.user.application.port.in;

import java.util.UUID;

public interface CancelMfaEnrollmentUseCase {

    void cancel(UUID userId);
}

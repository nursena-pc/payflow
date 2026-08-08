package com.nursena.payflow.user.application.port.in;

import java.util.UUID;

public interface GetMfaStatusUseCase {

    GetMfaStatusResult getStatus(UUID userId);
}

package com.nursena.payflow.user.application.port.in;

import java.util.UUID;

public interface GetCurrentUserProfileUseCase {

    GetCurrentUserProfileResult getProfile(UUID userId);
}

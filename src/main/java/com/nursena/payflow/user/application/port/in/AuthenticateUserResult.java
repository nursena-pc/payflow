package com.nursena.payflow.user.application.port.in;

public sealed interface AuthenticateUserResult
    permits AuthenticatedUserResult, MfaChallengeRequiredResult {
}

package com.nursena.payflow.user.adapter.in.web;

import java.time.Instant;

public record AuthenticateUserResponse(
    String accessToken,
    String tokenType,
    Instant expiresAt
) {
}

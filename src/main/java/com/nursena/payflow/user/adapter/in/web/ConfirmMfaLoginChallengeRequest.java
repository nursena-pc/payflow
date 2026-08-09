package com.nursena.payflow.user.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ConfirmMfaLoginChallengeRequest")
public record ConfirmMfaLoginChallengeRequest(
    @Schema(description = "Opaque challenge returned by password login.")
    String challengeToken,
    @Schema(
        description = "Six-digit TOTP or one unused MFA recovery code.",
        example = "123456"
    )
    String code
) {
    @Override
    public String toString() {
        return "ConfirmMfaLoginChallengeRequest[redacted]";
    }
}

package com.nursena.payflow.user.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(
    name = "RevokeCurrentRefreshSessionRequest",
    description =
        "Opaque refresh credential submitted to end "
            + "the represented refresh-token session."
)
public record RevokeCurrentRefreshSessionRequest(

    @Schema(
        description =
            "Opaque refresh token returned by login "
                + "or a previous refresh operation.",
        example =
            "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA",
        format = "password",
        accessMode = Schema.AccessMode.WRITE_ONLY
    )
    @NotBlank(
        message = "Refresh token is required."
    )
    String refreshToken
) {

    @Override
    public String toString() {
        return "RevokeCurrentRefreshSessionRequest[redacted]";
    }
}

package com.nursena.payflow.user.adapter.in.web;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "RegisterUserResponse",
    description = "Result of a successful registration."
)
public record RegisterUserResponse(

    @Schema(
        description = "Identifier of the registered user.",
        example =
            "8805681d-d537-42f2-8906-5da1f0666ab7",
        format = "uuid"
    )
    UUID userId
) {
}

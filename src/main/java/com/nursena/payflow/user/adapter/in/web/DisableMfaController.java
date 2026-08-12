package com.nursena.payflow.user.adapter.in.web;

import java.util.UUID;

import com.nursena.payflow.user.application.port.in.DisableMfaCommand;
import com.nursena.payflow.user.application.port.in.DisableMfaUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/mfa")
public class DisableMfaController {

    private final DisableMfaUseCase useCase;

    public DisableMfaController(
        DisableMfaUseCase useCase
    ) {
        this.useCase = useCase;
    }

    @DeleteMapping
    public ResponseEntity<Void> disable(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody DisableMfaRequest request
    ) {
        useCase.disable(
            new DisableMfaCommand(
                UUID.fromString(jwt.getSubject()),
                request.stepUpGrant()
            )
        );

        return ResponseEntity.noContent().build();
    }
}
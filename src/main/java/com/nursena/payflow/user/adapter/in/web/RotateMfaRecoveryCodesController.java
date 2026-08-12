package com.nursena.payflow.user.adapter.in.web;

import java.util.UUID;

import com.nursena.payflow.user.application.port.in.RotateMfaRecoveryCodesCommand;
import com.nursena.payflow.user.application.port.in.RotateMfaRecoveryCodesResult;
import com.nursena.payflow.user.application.port.in.RotateMfaRecoveryCodesUseCase;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/mfa/recovery-codes")
public class RotateMfaRecoveryCodesController {

    private final RotateMfaRecoveryCodesUseCase useCase;

    public RotateMfaRecoveryCodesController(
        RotateMfaRecoveryCodesUseCase useCase
    ) {
        this.useCase = useCase;
    }

    @PostMapping("/rotation")
    public ResponseEntity<RotateMfaRecoveryCodesResponse> rotate(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody RotateMfaRecoveryCodesRequest request
    ) {
        RotateMfaRecoveryCodesResult result = useCase.rotate(
            new RotateMfaRecoveryCodesCommand(
                UUID.fromString(jwt.getSubject()),
                request.stepUpGrant()
            )
        );

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(RotateMfaRecoveryCodesResponse.from(result));
    }
}
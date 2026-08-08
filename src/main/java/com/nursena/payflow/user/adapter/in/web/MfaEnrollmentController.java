package com.nursena.payflow.user.adapter.in.web;

import java.util.UUID;

import com.nursena.payflow.user.application.port.in.BeginMfaEnrollmentCommand;
import com.nursena.payflow.user.application.port.in.BeginMfaEnrollmentResult;
import com.nursena.payflow.user.application.port.in.BeginMfaEnrollmentUseCase;
import com.nursena.payflow.user.application.port.in.CancelMfaEnrollmentUseCase;
import com.nursena.payflow.user.application.port.in.ConfirmMfaEnrollmentCommand;
import com.nursena.payflow.user.application.port.in.ConfirmMfaEnrollmentResult;
import com.nursena.payflow.user.application.port.in.ConfirmMfaEnrollmentUseCase;
import com.nursena.payflow.user.application.port.in.GetMfaStatusResult;
import com.nursena.payflow.user.application.port.in.GetMfaStatusUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/mfa")
public class MfaEnrollmentController {

    private final GetMfaStatusUseCase statusUseCase;
    private final BeginMfaEnrollmentUseCase beginUseCase;
    private final ConfirmMfaEnrollmentUseCase confirmUseCase;
    private final CancelMfaEnrollmentUseCase cancelUseCase;

    public MfaEnrollmentController(
        GetMfaStatusUseCase statusUseCase,
        BeginMfaEnrollmentUseCase beginUseCase,
        ConfirmMfaEnrollmentUseCase confirmUseCase,
        CancelMfaEnrollmentUseCase cancelUseCase
    ) {
        this.statusUseCase = statusUseCase;
        this.beginUseCase = beginUseCase;
        this.confirmUseCase = confirmUseCase;
        this.cancelUseCase = cancelUseCase;
    }

    @GetMapping
    public ResponseEntity<MfaStatusResponse> getStatus(
        @AuthenticationPrincipal Jwt jwt
    ) {
        GetMfaStatusResult result = statusUseCase.getStatus(
            UUID.fromString(jwt.getSubject())
        );
        return ResponseEntity.ok(MfaStatusResponse.from(result));
    }

    @PostMapping("/enrollment")
    public ResponseEntity<MfaEnrollmentResponse> begin(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody BeginMfaEnrollmentRequest request
    ) {
        BeginMfaEnrollmentResult result = beginUseCase.begin(
            new BeginMfaEnrollmentCommand(
                UUID.fromString(jwt.getSubject()),
                request.currentPassword()
            )
        );
        return ResponseEntity.ok(MfaEnrollmentResponse.from(result));
    }

    @PostMapping("/enrollment/confirm")
    public ResponseEntity<MfaEnrollmentConfirmationResponse> confirm(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody ConfirmMfaEnrollmentRequest request
    ) {
        ConfirmMfaEnrollmentResult result = confirmUseCase.confirm(
            new ConfirmMfaEnrollmentCommand(
                UUID.fromString(jwt.getSubject()),
                request.code()
            )
        );
        return ResponseEntity.ok(
            MfaEnrollmentConfirmationResponse.from(result)
        );
    }

    @DeleteMapping("/enrollment")
    public ResponseEntity<Void> cancel(
        @AuthenticationPrincipal Jwt jwt
    ) {
        cancelUseCase.cancel(UUID.fromString(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }
}

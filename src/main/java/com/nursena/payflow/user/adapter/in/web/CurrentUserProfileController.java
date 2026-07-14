package com.nursena.payflow.user.adapter.in.web;

import java.util.UUID;

import com.nursena.payflow.user.application.port.in.GetCurrentUserProfileResult;
import com.nursena.payflow.user.application.port.in.GetCurrentUserProfileUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class CurrentUserProfileController {

    private final GetCurrentUserProfileUseCase profileUseCase;

    public CurrentUserProfileController(
        GetCurrentUserProfileUseCase profileUseCase
    ) {
        this.profileUseCase = profileUseCase;
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserProfileResponse>
    getCurrentUserProfile(
        @AuthenticationPrincipal Jwt jwt
    ) {
        UUID userId = UUID.fromString(
            jwt.getSubject()
        );

        GetCurrentUserProfileResult result =
            profileUseCase.getProfile(userId);

        return ResponseEntity.ok(
            CurrentUserProfileResponse.from(result)
        );
    }
}

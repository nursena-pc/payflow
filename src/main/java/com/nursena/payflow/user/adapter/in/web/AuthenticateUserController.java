package com.nursena.payflow.user.adapter.in.web;

import com.nursena.payflow.user.application.port.in.AuthenticateUserCommand;
import com.nursena.payflow.user.application.port.in.AuthenticateUserResult;
import com.nursena.payflow.user.application.port.in.AuthenticateUserUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticateUserController {

    private static final String TOKEN_TYPE = "Bearer";

    private final AuthenticateUserUseCase authenticateUserUseCase;

    public AuthenticateUserController(
        AuthenticateUserUseCase authenticateUserUseCase
    ) {
        this.authenticateUserUseCase = authenticateUserUseCase;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthenticateUserResponse> authenticate(
        @Valid @RequestBody AuthenticateUserRequest request
    ) {
        AuthenticateUserCommand command =
            new AuthenticateUserCommand(
                request.email(),
                request.password()
            );

        AuthenticateUserResult result =
            authenticateUserUseCase.authenticate(command);

        AuthenticateUserResponse response =
            new AuthenticateUserResponse(
                result.accessToken(),
                TOKEN_TYPE,
                result.expiresAt()
            );

        return ResponseEntity.ok(response);
    }
}

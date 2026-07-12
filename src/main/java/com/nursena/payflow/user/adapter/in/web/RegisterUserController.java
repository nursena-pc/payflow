package com.nursena.payflow.user.adapter.in.web;

import com.nursena.payflow.user.application.port.in.RegisterUserCommand;
import com.nursena.payflow.user.application.port.in.RegisterUserUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class RegisterUserController {

    private final RegisterUserUseCase registerUserUseCase;

    public RegisterUserController(RegisterUserUseCase registerUserUseCase) {
        this.registerUserUseCase = registerUserUseCase;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterUserResponse> register(
        @Valid @RequestBody RegisterUserRequest request
    ) {
        RegisterUserCommand command = new RegisterUserCommand(
            request.email(),
            request.password()
        );

        var userId = registerUserUseCase.register(command);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(new RegisterUserResponse(userId));
    }
}

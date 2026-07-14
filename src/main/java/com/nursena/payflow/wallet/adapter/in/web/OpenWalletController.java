package com.nursena.payflow.wallet.adapter.in.web;

import java.util.UUID;

import com.nursena.payflow.wallet.application.port.in.OpenWalletCommand;
import com.nursena.payflow.wallet.application.port.in.OpenWalletResult;
import com.nursena.payflow.wallet.application.port.in.OpenWalletUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallets")
public class OpenWalletController {

    private final OpenWalletUseCase openWalletUseCase;

    public OpenWalletController(
        OpenWalletUseCase openWalletUseCase
    ) {
        this.openWalletUseCase = openWalletUseCase;
    }

    @PostMapping
    public ResponseEntity<OpenWalletResponse> openWallet(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody OpenWalletRequest request
    ) {
        UUID ownerId = UUID.fromString(
            jwt.getSubject()
        );

        OpenWalletResult result =
            openWalletUseCase.open(
                new OpenWalletCommand(
                    ownerId,
                    request.currency()
                )
            );

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(OpenWalletResponse.from(result));
    }
}

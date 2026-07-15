package com.nursena.payflow.wallet.adapter.in.web;

import java.util.UUID;

import com.nursena.payflow.wallet.application.port.in.TopUpWalletCommand;
import com.nursena.payflow.wallet.application.port.in.TopUpWalletResult;
import com.nursena.payflow.wallet.application.port.in.TopUpWalletUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallets/me/top-ups")
public class TopUpWalletController {

    private final TopUpWalletUseCase topUpWalletUseCase;

    public TopUpWalletController(
        TopUpWalletUseCase topUpWalletUseCase
    ) {
        this.topUpWalletUseCase = topUpWalletUseCase;
    }

    @PostMapping
    public ResponseEntity<TopUpWalletResponse> topUpWallet(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody TopUpWalletRequest request
    ) {
        UUID ownerId = UUID.fromString(
            jwt.getSubject()
        );

        TopUpWalletResult result =
            topUpWalletUseCase.topUp(
                new TopUpWalletCommand(
                    ownerId,
                    request.amount()
                )
            );

        return ResponseEntity.ok(
            TopUpWalletResponse.from(result)
        );
    }
}

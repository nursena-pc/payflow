package com.nursena.payflow.wallet.adapter.in.web;

import java.util.UUID;

import com.nursena.payflow.wallet.application.port.in.GetCurrentWalletResult;
import com.nursena.payflow.wallet.application.port.in.GetCurrentWalletUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallets")
public class GetCurrentWalletController {

    private final GetCurrentWalletUseCase getCurrentWalletUseCase;

    public GetCurrentWalletController(
        GetCurrentWalletUseCase getCurrentWalletUseCase
    ) {
        this.getCurrentWalletUseCase =
            getCurrentWalletUseCase;
    }

    @GetMapping("/me")
    public ResponseEntity<GetCurrentWalletResponse>
    getCurrentWallet(
        @AuthenticationPrincipal Jwt jwt
    ) {
        UUID ownerId = UUID.fromString(
            jwt.getSubject()
        );

        GetCurrentWalletResult result =
            getCurrentWalletUseCase
                .getCurrentWallet(ownerId);

        return ResponseEntity.ok(
            GetCurrentWalletResponse.from(result)
        );
    }
}

package com.nursena.payflow.transaction.adapter.in.web;

import java.util.UUID;

import com.nursena.payflow.transaction.application.port.in.TransferMoneyCommand;
import com.nursena.payflow.transaction.application.port.in.TransferMoneyResult;
import com.nursena.payflow.transaction.application.port.in.TransferMoneyUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferMoneyController {

    private static final String IDEMPOTENCY_KEY_HEADER =
        "Idempotency-Key";

    private final TransferMoneyUseCase transferMoneyUseCase;

    public TransferMoneyController(
        TransferMoneyUseCase transferMoneyUseCase
    ) {
        this.transferMoneyUseCase = transferMoneyUseCase;
    }

    @PostMapping
    public ResponseEntity<TransferMoneyResponse> transfer(
        @AuthenticationPrincipal Jwt jwt,
        @RequestHeader(IDEMPOTENCY_KEY_HEADER)
        String idempotencyKey,
        @Valid @RequestBody TransferMoneyRequest request
    ) {
        UUID ownerId = UUID.fromString(
            jwt.getSubject()
        );

        TransferMoneyResult result =
            transferMoneyUseCase.transfer(
                new TransferMoneyCommand(
                    ownerId,
                    request.targetWalletId(),
                    request.amount(),
                    idempotencyKey
                )
            );

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                TransferMoneyResponse.from(result)
            );
    }
}

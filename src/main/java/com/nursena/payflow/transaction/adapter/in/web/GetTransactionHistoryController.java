package com.nursena.payflow.transaction.adapter.in.web;

import java.util.UUID;

import com.nursena.payflow.transaction.application.model.TransactionHistoryPage;
import com.nursena.payflow.transaction.application.port.in.GetTransactionHistoryQuery;
import com.nursena.payflow.transaction.application.port.in.GetTransactionHistoryUseCase;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
public class GetTransactionHistoryController {

    private final GetTransactionHistoryUseCase
        getTransactionHistoryUseCase;

    public GetTransactionHistoryController(
        GetTransactionHistoryUseCase
            getTransactionHistoryUseCase
    ) {
        this.getTransactionHistoryUseCase =
            getTransactionHistoryUseCase;
    }

    @GetMapping("/me")
    public ResponseEntity<TransactionHistoryResponse>
    getTransactionHistory(
        @AuthenticationPrincipal Jwt jwt,

        @RequestParam(defaultValue = "0")
        @Min(
            value = 0,
            message = "page must not be negative"
        )
        int page,

        @RequestParam(defaultValue = "20")
        @Min(
            value = 1,
            message = "size must be greater than zero"
        )
        @Max(
            value = GetTransactionHistoryQuery.MAX_SIZE,
            message = "size must not exceed 100"
        )
        int size
    ) {
        UUID ownerId = UUID.fromString(
            jwt.getSubject()
        );

        TransactionHistoryPage result =
            getTransactionHistoryUseCase
                .getTransactionHistory(
                    new GetTransactionHistoryQuery(
                        ownerId,
                        page,
                        size
                    )
                );

        return ResponseEntity.ok(
            TransactionHistoryResponse.from(result)
        );
    }
}

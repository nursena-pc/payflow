package com.nursena.payflow.configuration;

public final class TransactionApiExamples {

    public static final String TRANSFER_SUCCESS =
        """
        {
          "transactionId": "aa3ab0b3-3d85-42d7-a641-2ec43cd81dd9",
          "sourceWalletId": "4a2d98c0-2673-4d21-b5c1-9b69833db721",
          "targetWalletId": "f4c8ab12-a4bb-43cb-b6a8-e797cc95e914",
          "amount": 125.50,
          "currency": "TRY",
          "status": "COMPLETED",
          "createdAt": "2026-07-17T12:00:00Z",
          "completedAt": "2026-07-17T12:00:00.125Z"
        }
        """;

    public static final String MISSING_IDEMPOTENCY_KEY =
        """
        {
          "timestamp": "2026-07-17T12:00:00Z",
          "status": 400,
          "code": "MISSING_IDEMPOTENCY_KEY",
          "message": "Idempotency-Key header is required.",
          "path": "/api/v1/transfers",
          "violations": []
        }
        """;

    public static final String TRANSFER_VALIDATION_ERROR =
        """
        {
          "timestamp": "2026-07-17T12:00:00Z",
          "status": 400,
          "code": "VALIDATION_FAILED",
          "message": "Request validation failed.",
          "path": "/api/v1/transfers",
          "violations": [
            {
              "field": "amount",
              "message": "amount must be greater than zero"
            }
          ]
        }
        """;

    public static final String TRANSFER_WALLET_NOT_FOUND =
        """
        {
          "timestamp": "2026-07-17T12:00:00Z",
          "status": 404,
          "code": "WALLET_NOT_FOUND",
          "message": "Wallet could not be found.",
          "path": "/api/v1/transfers",
          "violations": []
        }
        """;

    public static final String IDEMPOTENCY_KEY_CONFLICT =
        """
        {
          "timestamp": "2026-07-17T12:00:00Z",
          "status": 409,
          "code": "IDEMPOTENCY_KEY_CONFLICT",
          "message": "Idempotency key has already been used for another transfer request.",
          "path": "/api/v1/transfers",
          "violations": []
        }
        """;

    public static final String IDEMPOTENCY_REQUEST_IN_PROGRESS =
        """
        {
          "timestamp": "2026-07-17T12:00:00Z",
          "status": 409,
          "code": "IDEMPOTENCY_REQUEST_IN_PROGRESS",
          "message": "A transfer with this idempotency key is still being processed.",
          "path": "/api/v1/transfers",
          "violations": []
        }
        """;

    public static final String TRANSFER_CONCURRENT_UPDATE =
        """
        {
          "timestamp": "2026-07-17T12:00:00Z",
          "status": 409,
          "code": "WALLET_CONCURRENT_UPDATE",
          "message": "Wallet was updated concurrently. Please retry the operation.",
          "path": "/api/v1/transfers",
          "violations": []
        }
        """;

    public static final String INVALID_IDEMPOTENCY_KEY =
        """
        {
          "timestamp": "2026-07-17T12:00:00Z",
          "status": 422,
          "code": "INVALID_IDEMPOTENCY_KEY",
          "message": "Idempotency key must contain between 1 and 100 characters.",
          "path": "/api/v1/transfers",
          "violations": []
        }
        """;

    public static final String INSUFFICIENT_BALANCE =
        """
        {
          "timestamp": "2026-07-17T12:00:00Z",
          "status": 422,
          "code": "INSUFFICIENT_BALANCE",
          "message": "Wallet balance is insufficient for this operation.",
          "path": "/api/v1/transfers",
          "violations": []
        }
        """;

    public static final String TRANSFER_WALLET_NOT_ACTIVE =
        """
        {
          "timestamp": "2026-07-17T12:00:00Z",
          "status": 422,
          "code": "WALLET_NOT_ACTIVE",
          "message": "Wallet must be active to perform this operation.",
          "path": "/api/v1/transfers",
          "violations": []
        }
        """;

    public static final String TRANSFER_CURRENCY_MISMATCH =
        """
        {
          "timestamp": "2026-07-17T12:00:00Z",
          "status": 422,
          "code": "TRANSFER_CURRENCY_MISMATCH",
          "message": "Source and target wallet currencies must match.",
          "path": "/api/v1/transfers",
          "violations": []
        }
        """;

    public static final String TRANSACTION_HISTORY_SUCCESS =
        """
        {
          "items": [
            {
              "transactionId": "aa3ab0b3-3d85-42d7-a641-2ec43cd81dd9",
              "type": "TRANSFER",
              "direction": "OUTGOING",
              "counterpartyWalletId": "f4c8ab12-a4bb-43cb-b6a8-e797cc95e914",
              "amount": 125.50,
              "currency": "TRY",
              "status": "COMPLETED",
              "createdAt": "2026-07-17T12:00:00Z",
              "completedAt": "2026-07-17T12:00:00.125Z"
            }
          ],
          "page": 0,
          "size": 20,
          "totalElements": 1,
          "totalPages": 1,
          "first": true,
          "last": true,
          "hasNext": false,
          "hasPrevious": false
        }
        """;

    public static final String
        TRANSACTION_HISTORY_VALIDATION_ERROR =
        """
        {
          "timestamp": "2026-07-17T12:00:00Z",
          "status": 400,
          "code": "VALIDATION_FAILED",
          "message": "Request validation failed.",
          "path": "/api/v1/transactions/me",
          "violations": []
        }
        """;

    public static final String
        TRANSACTION_HISTORY_WALLET_NOT_FOUND =
        """
        {
          "timestamp": "2026-07-17T12:00:00Z",
          "status": 404,
          "code": "WALLET_NOT_FOUND",
          "message": "Wallet could not be found.",
          "path": "/api/v1/transactions/me",
          "violations": []
        }
        """;

    private TransactionApiExamples() {
    }
}

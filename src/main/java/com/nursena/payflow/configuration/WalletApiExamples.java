package com.nursena.payflow.configuration;

public final class WalletApiExamples {

    public static final String OPEN_WALLET =
        """
        {
          "id": "4a2d98c0-2673-4d21-b5c1-9b69833db721",
          "ownerId": "8805681d-d537-42f2-8906-5da1f0666ab7",
          "balance": 0.00,
          "currency": "TRY",
          "status": "ACTIVE",
          "createdAt": "2026-07-17T12:00:00Z"
        }
        """;

    public static final String OPEN_WALLET_VALIDATION_ERROR =
        """
        {
          "timestamp": "2026-07-17T12:00:00Z",
          "status": 400,
          "code": "VALIDATION_FAILED",
          "message": "Request validation failed.",
          "path": "/api/v1/wallets",
          "violations": [
            {
              "field": "currency",
              "message": "Currency is required."
            }
          ]
        }
        """;

    public static final String WALLET_ALREADY_EXISTS =
        """
        {
          "timestamp": "2026-07-17T12:00:00Z",
          "status": 409,
          "code": "WALLET_ALREADY_EXISTS",
          "message": "User already has a wallet.",
          "path": "/api/v1/wallets",
          "violations": []
        }
        """;

    public static final String CURRENT_WALLET =
        """
        {
          "id": "4a2d98c0-2673-4d21-b5c1-9b69833db721",
          "balance": 250.00,
          "currency": "TRY",
          "status": "ACTIVE",
          "createdAt": "2026-07-17T12:00:00Z"
        }
        """;

    public static final String CURRENT_WALLET_NOT_FOUND =
        """
        {
          "timestamp": "2026-07-17T12:00:00Z",
          "status": 404,
          "code": "WALLET_NOT_FOUND",
          "message": "Wallet could not be found.",
          "path": "/api/v1/wallets/me",
          "violations": []
        }
        """;

    public static final String TOP_UP_WALLET =
        """
        {
          "id": "4a2d98c0-2673-4d21-b5c1-9b69833db721",
          "balance": 350.00,
          "currency": "TRY",
          "status": "ACTIVE",
          "createdAt": "2026-07-17T12:00:00Z"
        }
        """;

    public static final String TOP_UP_VALIDATION_ERROR =
        """
        {
          "timestamp": "2026-07-17T12:00:00Z",
          "status": 400,
          "code": "VALIDATION_FAILED",
          "message": "Request validation failed.",
          "path": "/api/v1/wallets/me/top-ups",
          "violations": [
            {
              "field": "amount",
              "message": "amount must be greater than zero"
            }
          ]
        }
        """;

    public static final String TOP_UP_WALLET_NOT_FOUND =
        """
        {
          "timestamp": "2026-07-17T12:00:00Z",
          "status": 404,
          "code": "WALLET_NOT_FOUND",
          "message": "Wallet could not be found.",
          "path": "/api/v1/wallets/me/top-ups",
          "violations": []
        }
        """;

    public static final String WALLET_CONCURRENT_UPDATE =
        """
        {
          "timestamp": "2026-07-17T12:00:00Z",
          "status": 409,
          "code": "WALLET_CONCURRENT_UPDATE",
          "message": "Wallet was updated concurrently. Please retry the operation.",
          "path": "/api/v1/wallets/me/top-ups",
          "violations": []
        }
        """;

    public static final String WALLET_NOT_ACTIVE =
        """
        {
          "timestamp": "2026-07-17T12:00:00Z",
          "status": 422,
          "code": "WALLET_NOT_ACTIVE",
          "message": "Wallet must be active to perform this operation.",
          "path": "/api/v1/wallets/me/top-ups",
          "violations": []
        }
        """;

    private WalletApiExamples() {
    }
}

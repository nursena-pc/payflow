package com.nursena.payflow.configuration;

public final class OpenApiExamples {

    public static final String SYSTEM_HEALTH =
        """
        {
          "status": "UP",
          "service": "payflow",
          "timestamp": "2026-07-17T12:00:00Z"
        }
        """;

    public static final String REGISTER_SUCCESS =
        """
        {
          "userId": "8805681d-d537-42f2-8906-5da1f0666ab7"
        }
        """;

    public static final String LOGIN_SUCCESS =
        """
        {
          "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
          "tokenType": "Bearer",
          "expiresAt": "2026-07-17T12:15:00Z"
        }
        """;

    public static final String REGISTER_VALIDATION_ERROR =
        """
        {
          "timestamp": "2026-07-17T12:00:00Z",
          "status": 400,
          "code": "VALIDATION_FAILED",
          "message": "Request validation failed.",
          "path": "/api/v1/auth/register",
          "violations": [
            {
              "field": "password",
              "message": "Password must be between 12 and 72 characters."
            }
          ]
        }
        """;

    public static final String LOGIN_VALIDATION_ERROR =
        """
        {
          "timestamp": "2026-07-17T12:00:00Z",
          "status": 400,
          "code": "VALIDATION_FAILED",
          "message": "Request validation failed.",
          "path": "/api/v1/auth/login",
          "violations": [
            {
              "field": "email",
              "message": "Email must be valid."
            }
          ]
        }
        """;

    public static final String EMAIL_ALREADY_REGISTERED =
        """
        {
          "timestamp": "2026-07-17T12:00:00Z",
          "status": 409,
          "code": "EMAIL_ALREADY_REGISTERED",
          "message": "A user with this email address already exists.",
          "path": "/api/v1/auth/register",
          "violations": []
        }
        """;

    public static final String INVALID_CREDENTIALS =
        """
        {
          "timestamp": "2026-07-17T12:00:00Z",
          "status": 401,
          "code": "INVALID_CREDENTIALS",
          "message": "Email or password is incorrect.",
          "path": "/api/v1/auth/login",
          "violations": []
        }
        """;

    public static final String USER_ACCOUNT_UNAVAILABLE =
        """
        {
          "timestamp": "2026-07-17T12:00:00Z",
          "status": 403,
          "code": "USER_ACCOUNT_UNAVAILABLE",
          "message": "User account is not available for authentication.",
          "path": "/api/v1/auth/login",
          "violations": []
        }
        """;

    private OpenApiExamples() {
    }
}

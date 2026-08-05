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
          "expiresAt": "2026-07-17T12:15:00Z",
          "refreshToken": "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA",
          "refreshTokenExpiresAt": "2026-07-24T12:00:00Z"
        }
        """;

    public static final String REFRESH_SUCCESS =
        """
        {
          "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
          "tokenType": "Bearer",
          "expiresAt": "2026-07-28T12:15:00Z",
          "refreshToken": "ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8",
          "refreshTokenExpiresAt": "2026-08-04T12:00:00Z"
        }
        """;

    public static final String REFRESH_VALIDATION_ERROR =
        """
        {
          "timestamp": "2026-07-28T12:00:00Z",
          "status": 400,
          "code": "VALIDATION_FAILED",
          "message": "Request validation failed.",
          "path": "/api/v1/auth/refresh",
          "violations": [
            {
              "field": "refreshToken",
              "message": "Refresh token is required."
            }
          ]
        }
        """;

    public static final String LOGOUT_VALIDATION_ERROR =
        """
        {
          "timestamp": "2026-07-29T12:00:00Z",
          "status": 400,
          "code": "VALIDATION_FAILED",
          "message": "Request validation failed.",
          "path": "/api/v1/auth/logout",
          "violations": [
            {
              "field": "refreshToken",
              "message": "Refresh token is required."
            }
          ]
        }
        """;

    public static final String INVALID_REFRESH_TOKEN =
        """
        {
          "timestamp": "2026-07-28T12:00:00Z",
          "status": 401,
          "code": "REFRESH_TOKEN_INVALID",
          "message": "Refresh token is invalid.",
          "path": "/api/v1/auth/refresh",
          "violations": []
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

    public static final String LOGIN_RATE_LIMIT_EXCEEDED =
        """
        {
          "timestamp": "2026-07-30T12:00:00Z",
          "status": 429,
          "code": "LOGIN_RATE_LIMIT_EXCEEDED",
          "message": "Too many login attempts. Try again later.",
          "path": "/api/v1/auth/login",
          "violations": []
        }
        """;

    public static final String LOGIN_RATE_LIMIT_UNAVAILABLE =
        """
        {
          "timestamp": "2026-07-30T12:00:00Z",
          "status": 503,
          "code": "LOGIN_RATE_LIMIT_UNAVAILABLE",
          "message": "Login protection is temporarily unavailable.",
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

    public static final String
    EMAIL_VERIFICATION_REQUEST_VALIDATION_ERROR =
        """
        {
          "timestamp": "2026-08-05T12:00:00Z",
          "status": 400,
          "code": "VALIDATION_FAILED",
          "message": "Request validation failed.",
          "path": "/api/v1/auth/email-verification/requests",
          "violations": [
            {
              "field": "email",
              "message": "Email must be valid."
            }
          ]
        }
        """;

    public static final String
    EMAIL_VERIFICATION_CONFIRM_VALIDATION_ERROR =
        """
        {
          "timestamp": "2026-08-05T12:00:00Z",
          "status": 400,
          "code": "VALIDATION_FAILED",
          "message": "Request validation failed.",
          "path": "/api/v1/auth/email-verification/confirm",
          "violations": [
            {
              "field": "credential",
              "message": "Email verification credential is required."
            }
          ]
        }
        """;

    public static final String
    INVALID_ACCOUNT_ACTION_CREDENTIAL =
        """
        {
          "timestamp": "2026-08-05T12:00:00Z",
          "status": 422,
          "code": "ACCOUNT_ACTION_CREDENTIAL_INVALID",
          "message": "Account action credential is invalid.",
          "path": "/api/v1/auth/email-verification/confirm",
          "violations": []
        }
        """;


    public static final String
    PASSWORD_RECOVERY_REQUEST_VALIDATION_ERROR =
        """
        {
          "timestamp": "2026-08-05T12:00:00Z",
          "status": 400,
          "code": "VALIDATION_FAILED",
          "message": "Request validation failed.",
          "path": "/api/v1/auth/password-recovery/requests",
          "violations": [
            {
              "field": "email",
              "message": "Email must be valid."
            }
          ]
        }
        """;

    public static final String
    PASSWORD_RECOVERY_CONFIRM_VALIDATION_ERROR =
        """
        {
          "timestamp": "2026-08-05T12:00:00Z",
          "status": 400,
          "code": "VALIDATION_FAILED",
          "message": "Request validation failed.",
          "path": "/api/v1/auth/password-recovery/confirm",
          "violations": [
            {
              "field": "newPassword",
              "message": "New password must be between 12 and 72 characters."
            }
          ]
        }
        """;

    public static final String
    INVALID_PASSWORD_RECOVERY_CREDENTIAL =
        """
        {
          "timestamp": "2026-08-05T12:00:00Z",
          "status": 422,
          "code": "ACCOUNT_ACTION_CREDENTIAL_INVALID",
          "message": "Account action credential is invalid.",
          "path": "/api/v1/auth/password-recovery/confirm",
          "violations": []
        }
        """;

    private OpenApiExamples() {
    }
}

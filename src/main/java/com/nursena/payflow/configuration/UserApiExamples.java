package com.nursena.payflow.configuration;

public final class UserApiExamples {

    public static final String CURRENT_USER_PROFILE =
        """
        {
          "id": "8805681d-d537-42f2-8906-5da1f0666ab7",
          "email": "nursena@example.com",
          "role": "USER",
          "status": "ACTIVE",
          "createdAt": "2026-07-17T12:00:00Z"
        }
        """;

    public static final String USER_NOT_FOUND =
        """
        {
          "timestamp": "2026-07-17T12:00:00Z",
          "status": 404,
          "code": "USER_NOT_FOUND",
          "message": "User could not be found.",
          "path": "/api/v1/users/me",
          "violations": []
        }
        """;

    private UserApiExamples() {
    }
}

package com.nursena.payflow.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.nursena.payflow.configuration.SecurityConfiguration;
import com.nursena.payflow.user.application.port.in.AuthenticateUserCommand;
import com.nursena.payflow.user.application.port.in.AuthenticateUserResult;
import com.nursena.payflow.user.application.port.in.AuthenticateUserUseCase;
import com.nursena.payflow.user.domain.exception.InvalidCredentialsException;
import com.nursena.payflow.user.domain.exception.UserAccountUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthenticateUserController.class)
@Import({
    SecurityConfiguration.class,
    UserAuthenticationExceptionHandler.class
})
class AuthenticateUserControllerTest {

    private static final Instant ACCESS_EXPIRES_AT =
        Instant.parse(
            "2026-07-28T12:15:00Z"
        );

    private static final Instant REFRESH_EXPIRES_AT =
        Instant.parse(
            "2026-08-04T12:00:00Z"
        );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticateUserUseCase
        authenticateUserUseCase;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldAuthenticateUserAndReturnCredentialPair()
        throws Exception {

        when(authenticateUserUseCase.authenticate(
            any(AuthenticateUserCommand.class)
        ))
            .thenReturn(
                new AuthenticateUserResult(
                    "signed-access-token",
                    ACCESS_EXPIRES_AT,
                    "opaque-refresh-token",
                    REFRESH_EXPIRES_AT
                )
            );

        mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content("""
                        {
                          "email": "nursena@example.com",
                          "password": "StrongPassword123!"
                        }
                        """)
            )
            .andExpect(
                status().isOk()
            )
            .andExpect(
                jsonPath(
                    "$.accessToken"
                ).value(
                    "signed-access-token"
                )
            )
            .andExpect(
                jsonPath(
                    "$.tokenType"
                ).value("Bearer")
            )
            .andExpect(
                jsonPath(
                    "$.expiresAt"
                ).value(
                    ACCESS_EXPIRES_AT.toString()
                )
            )
            .andExpect(
                jsonPath(
                    "$.refreshToken"
                ).value(
                    "opaque-refresh-token"
                )
            )
            .andExpect(
                jsonPath(
                    "$.refreshTokenExpiresAt"
                ).value(
                    REFRESH_EXPIRES_AT.toString()
                )
            )
            .andExpect(
                jsonPath(
                    "$.tokenDigest"
                ).doesNotExist()
            )
            .andExpect(
                jsonPath(
                    "$.familyId"
                ).doesNotExist()
            );

        verify(authenticateUserUseCase)
            .authenticate(
                argThat(command ->
                    command.email().equals(
                        "nursena@example.com"
                    )
                        && command.rawPassword()
                            .equals(
                                "StrongPassword123!"
                            )
                )
            );
    }

    @Test
    void shouldRedactCredentialValuesFromResponseToString() {
        AuthenticateUserResponse response =
            new AuthenticateUserResponse(
                "secret-access-token",
                "Bearer",
                ACCESS_EXPIRES_AT,
                "secret-refresh-token",
                REFRESH_EXPIRES_AT
            );

        assertThat(response.toString())
            .isEqualTo(
                "AuthenticateUserResponse[redacted]"
            )
            .doesNotContain(
                "secret-access-token",
                "secret-refresh-token"
            );
    }

    @Test
    void shouldRejectInvalidEmail()
        throws Exception {

        mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content("""
                        {
                          "email": "not-an-email",
                          "password": "StrongPassword123!"
                        }
                        """)
            )
            .andExpect(
                status().isBadRequest()
            )
            .andExpect(
                jsonPath(
                    "$.code"
                ).value(
                    "VALIDATION_FAILED"
                )
            )
            .andExpect(
                jsonPath(
                    "$.violations[0].field"
                ).value("email")
            );
    }

    @Test
    void shouldReturnUnauthorizedForInvalidCredentials()
        throws Exception {

        when(authenticateUserUseCase.authenticate(
            any(AuthenticateUserCommand.class)
        ))
            .thenThrow(
                new InvalidCredentialsException()
            );

        mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content("""
                        {
                          "email": "nursena@example.com",
                          "password": "WrongPassword123!"
                        }
                        """)
            )
            .andExpect(
                status().isUnauthorized()
            )
            .andExpect(
                jsonPath(
                    "$.code"
                ).value(
                    "INVALID_CREDENTIALS"
                )
            )
            .andExpect(
                jsonPath(
                    "$.message"
                ).value(
                    "Email or password is incorrect."
                )
            )
            .andExpect(
                jsonPath(
                    "$.path"
                ).value(
                    "/api/v1/auth/login"
                )
            );
    }

    @Test
    void shouldReturnForbiddenForUnavailableAccount()
        throws Exception {

        when(authenticateUserUseCase.authenticate(
            any(AuthenticateUserCommand.class)
        ))
            .thenThrow(
                new UserAccountUnavailableException()
            );

        mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content("""
                        {
                          "email": "nursena@example.com",
                          "password": "StrongPassword123!"
                        }
                        """)
            )
            .andExpect(
                status().isForbidden()
            )
            .andExpect(
                jsonPath(
                    "$.code"
                ).value(
                    "USER_ACCOUNT_UNAVAILABLE"
                )
            )
            .andExpect(
                jsonPath(
                    "$.message"
                ).value(
                    "User account is not available for authentication."
                )
            )
            .andExpect(
                jsonPath(
                    "$.path"
                ).value(
                    "/api/v1/auth/login"
                )
            );
    }
}

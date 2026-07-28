package com.nursena.payflow.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.nursena.payflow.configuration.SecurityConfiguration;
import com.nursena.payflow.user.application.port.in.RotateRefreshCredentialsCommand;
import com.nursena.payflow.user.application.port.in.RotateRefreshCredentialsResult;
import com.nursena.payflow.user.application.port.in.RotateRefreshCredentialsUseCase;
import com.nursena.payflow.user.domain.exception.InvalidRefreshTokenException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    RotateRefreshCredentialsController.class
)
@Import({
    SecurityConfiguration.class,
    UserAuthenticationExceptionHandler.class
})
class RotateRefreshCredentialsControllerTest {

    private static final String CURRENT_TOKEN =
        "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA";

    private static final String SUCCESSOR_TOKEN =
        "ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8";

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
    private RotateRefreshCredentialsUseCase
        rotateRefreshCredentialsUseCase;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldRotateCredentialsWithoutBearerAuthentication()
        throws Exception {

        when(rotateRefreshCredentialsUseCase.rotate(
            any(RotateRefreshCredentialsCommand.class)
        ))
            .thenReturn(
                new RotateRefreshCredentialsResult(
                    "signed-access-token",
                    ACCESS_EXPIRES_AT,
                    SUCCESSOR_TOKEN,
                    REFRESH_EXPIRES_AT
                )
            );

        mockMvc.perform(
                post("/api/v1/auth/refresh")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content("""
                        {
                          "refreshToken": "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA"
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
                    SUCCESSOR_TOKEN
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
            )
            .andExpect(
                jsonPath(
                    "$.recordId"
                ).doesNotExist()
            );

        verify(rotateRefreshCredentialsUseCase)
            .rotate(
                argThat(command ->
                    command.refreshToken()
                        .equals(CURRENT_TOKEN)
                )
            );
    }

    @Test
    void shouldRejectBlankRefreshToken()
        throws Exception {

        mockMvc.perform(
                post("/api/v1/auth/refresh")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content("""
                        {
                          "refreshToken": " "
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
                    "$.path"
                ).value(
                    "/api/v1/auth/refresh"
                )
            )
            .andExpect(
                jsonPath(
                    "$.violations[0].field"
                ).value(
                    "refreshToken"
                )
            );

        verifyNoInteractions(
            rotateRefreshCredentialsUseCase
        );
    }

    @Test
    void shouldReturnUnauthorizedForInvalidRefreshToken()
        throws Exception {

        when(rotateRefreshCredentialsUseCase.rotate(
            any(RotateRefreshCredentialsCommand.class)
        ))
            .thenThrow(
                new InvalidRefreshTokenException()
            );

        mockMvc.perform(
                post("/api/v1/auth/refresh")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content("""
                        {
                          "refreshToken": "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA"
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
                    "REFRESH_TOKEN_INVALID"
                )
            )
            .andExpect(
                jsonPath(
                    "$.message"
                ).value(
                    "Refresh token is invalid."
                )
            )
            .andExpect(
                jsonPath(
                    "$.path"
                ).value(
                    "/api/v1/auth/refresh"
                )
            )
            .andExpect(
                jsonPath(
                    "$.violations"
                ).isEmpty()
            );
    }

    @Test
    void shouldRedactRefreshTokenFromRequestToString() {
        RotateRefreshCredentialsRequest request =
            new RotateRefreshCredentialsRequest(
                "secret-refresh-token"
            );

        assertThat(request.toString())
            .isEqualTo(
                "RotateRefreshCredentialsRequest[redacted]"
            )
            .doesNotContain(
                "secret-refresh-token"
            );
    }

    @Test
    void shouldRedactCredentialsFromResponseToString() {
        RotateRefreshCredentialsResponse response =
            new RotateRefreshCredentialsResponse(
                "secret-access-token",
                "Bearer",
                ACCESS_EXPIRES_AT,
                "secret-refresh-token",
                REFRESH_EXPIRES_AT
            );

        assertThat(response.toString())
            .isEqualTo(
                "RotateRefreshCredentialsResponse[redacted]"
            )
            .doesNotContain(
                "secret-access-token",
                "secret-refresh-token"
            );
    }
}

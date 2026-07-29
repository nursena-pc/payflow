package com.nursena.payflow.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nursena.payflow.configuration.SecurityConfiguration;
import com.nursena.payflow.user.application.port.in.RevokeCurrentRefreshSessionUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    RevokeCurrentRefreshSessionController.class
)
@Import(SecurityConfiguration.class)
class RevokeCurrentRefreshSessionControllerTest {

    private static final String CURRENT_TOKEN =
        "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RevokeCurrentRefreshSessionUseCase
        revokeCurrentRefreshSessionUseCase;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldLogoutWithoutBearerAuthentication()
        throws Exception {

        mockMvc.perform(
                post("/api/v1/auth/logout")
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
                status().isNoContent()
            )
            .andExpect(
                content().string("")
            );

        verify(revokeCurrentRefreshSessionUseCase)
            .revoke(
                argThat(command ->
                    command.refreshToken()
                        .equals(CURRENT_TOKEN)
                )
            );
    }

    @Test
    void shouldReturnNoContentForMalformedCredential()
        throws Exception {

        mockMvc.perform(
                post("/api/v1/auth/logout")
                    .contentType(
                        MediaType.APPLICATION_JSON
                    )
                    .content("""
                        {
                          "refreshToken": "not-canonical"
                        }
                        """)
            )
            .andExpect(
                status().isNoContent()
            )
            .andExpect(
                content().string("")
            );

        verify(revokeCurrentRefreshSessionUseCase)
            .revoke(
                argThat(command ->
                    command.refreshToken()
                        .equals("not-canonical")
                )
            );
    }

    @Test
    void shouldRejectBlankRefreshToken()
        throws Exception {

        mockMvc.perform(
                post("/api/v1/auth/logout")
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
                    "/api/v1/auth/logout"
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
            revokeCurrentRefreshSessionUseCase
        );
    }

    @Test
    void shouldRedactRefreshTokenFromRequestToString() {
        RevokeCurrentRefreshSessionRequest request =
            new RevokeCurrentRefreshSessionRequest(
                "secret-refresh-token"
            );

        assertThat(request.toString())
            .isEqualTo(
                "RevokeCurrentRefreshSessionRequest[redacted]"
            )
            .doesNotContain(
                "secret-refresh-token"
            );
    }
}

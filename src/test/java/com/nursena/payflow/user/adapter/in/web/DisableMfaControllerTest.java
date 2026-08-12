package com.nursena.payflow.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.configuration.SecurityConfiguration;
import com.nursena.payflow.observability.adapter.in.web.RequestCorrelationConfiguration;
import com.nursena.payflow.user.application.exception.MfaSecurityUnavailableException;
import com.nursena.payflow.user.application.port.in.DisableMfaCommand;
import com.nursena.payflow.user.application.port.in.DisableMfaUseCase;
import com.nursena.payflow.user.domain.exception.InvalidStepUpGrantException;
import com.nursena.payflow.user.domain.exception.MfaStateConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(DisableMfaController.class)
@Import({
    RequestCorrelationConfiguration.class,
    SecurityConfiguration.class,
    StepUpExceptionHandler.class
})
class DisableMfaControllerTest {

    private static final String ENDPOINT =
        "/api/v1/users/me/mfa";

    private static final UUID USER_ID =
        UUID.fromString(
            "10000000-0000-0000-0000-000000000105"
        );

    @Autowired MockMvc mockMvc;
    @MockitoBean DisableMfaUseCase useCase;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void shouldRequireBearerAuthentication() throws Exception {
        mockMvc.perform(delete(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(useCase);
    }

    @Test
    void shouldRejectBlankStepUpGrant() throws Exception {
        mockJwt();

        mockMvc.perform(delete(ENDPOINT)
                .header(AUTHORIZATION, "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "stepUpGrant": " "
                    }
                    """))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(useCase);
    }

    @Test
    void shouldDisableMfaForAuthenticatedSubject()
        throws Exception {

        mockJwt();

        performValid()
            .andExpect(status().isNoContent());

        verify(useCase).disable(argThat(command ->
            command.userId().equals(USER_ID)
                && command.stepUpGrant().equals(
                    "opaque-grant"
                )
        ));
    }

    @Test
    void shouldExposeStableCoarseFailureContracts()
        throws Exception {

        mockJwt();

        doThrow(new InvalidStepUpGrantException())
            .doThrow(new MfaStateConflictException())
            .doThrow(new MfaSecurityUnavailableException())
            .when(useCase)
            .disable(any(DisableMfaCommand.class));

        performValid()
            .andExpect(status().isForbidden())
            .andExpect(
                jsonPath("$.code")
                    .value("STEP_UP_INVALID")
            );

        performValid()
            .andExpect(status().isConflict())
            .andExpect(
                jsonPath("$.code")
                    .value("MFA_STATE_CONFLICT")
            );

        performValid()
            .andExpect(status().isServiceUnavailable())
            .andExpect(
                jsonPath("$.code")
                    .value("MFA_SECURITY_UNAVAILABLE")
            );
    }

    @Test
    void shouldRedactStepUpGrantFromToString() {
        DisableMfaRequest request =
            new DisableMfaRequest("opaque-grant");

        assertThat(request.toString())
            .doesNotContain("opaque-grant");
    }

    private ResultActions performValid() throws Exception {
        return mockMvc.perform(delete(ENDPOINT)
            .header(AUTHORIZATION, "Bearer token")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validRequest()));
    }

    private static String validRequest() {
        return """
            {
              "stepUpGrant": "opaque-grant"
            }
            """;
    }

    private void mockJwt() {
        when(jwtDecoder.decode("token")).thenReturn(
            Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(USER_ID.toString())
                .claim("role", "USER")
                .issuedAt(
                    Instant.parse(
                        "2026-08-10T09:55:00Z"
                    )
                )
                .expiresAt(
                    Instant.parse(
                        "2026-08-10T10:15:00Z"
                    )
                )
                .build()
        );
    }
}
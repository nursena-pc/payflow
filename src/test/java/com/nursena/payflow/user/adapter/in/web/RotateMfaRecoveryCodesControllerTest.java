package com.nursena.payflow.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.CACHE_CONTROL;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.nursena.payflow.configuration.SecurityConfiguration;
import com.nursena.payflow.observability.adapter.in.web.RequestCorrelationConfiguration;
import com.nursena.payflow.user.application.exception.MfaSecurityUnavailableException;
import com.nursena.payflow.user.application.port.in.RotateMfaRecoveryCodesCommand;
import com.nursena.payflow.user.application.port.in.RotateMfaRecoveryCodesResult;
import com.nursena.payflow.user.application.port.in.RotateMfaRecoveryCodesUseCase;
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

@WebMvcTest(RotateMfaRecoveryCodesController.class)
@Import({
    RequestCorrelationConfiguration.class,
    SecurityConfiguration.class,
    StepUpExceptionHandler.class
})
class RotateMfaRecoveryCodesControllerTest {

    private static final String ENDPOINT =
        "/api/v1/users/me/mfa/recovery-codes/rotation";

    private static final UUID USER_ID =
        UUID.fromString("10000000-0000-0000-0000-000000000105");

    @Autowired MockMvc mockMvc;
    @MockitoBean RotateMfaRecoveryCodesUseCase useCase;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void shouldRequireBearerAuthentication() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRequest()))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(useCase);
    }

    @Test
    void shouldRejectBlankStepUpGrant() throws Exception {
        mockJwt();

        mockMvc.perform(post(ENDPOINT)
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
    void shouldRotateRecoveryCodesForAuthenticatedSubject()
        throws Exception {

        mockJwt();

        when(useCase.rotate(any(RotateMfaRecoveryCodesCommand.class)))
            .thenReturn(result());

        performValid()
            .andExpect(status().isOk())
            .andExpect(header().string(CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.recoveryCodes.length()").value(2))
            .andExpect(
                jsonPath("$.recoveryCodes[0]")
                    .value("recovery-code-one")
            )
            .andExpect(
                jsonPath("$.recoveryCodes[1]")
                    .value("recovery-code-two")
            );

        verify(useCase).rotate(argThat(command ->
            command.userId().equals(USER_ID)
                && command.stepUpGrant().equals("opaque-grant")
        ));
    }

    @Test
    void shouldExposeStableCoarseFailureContracts() throws Exception {
        mockJwt();

        when(useCase.rotate(any()))
            .thenThrow(new InvalidStepUpGrantException())
            .thenThrow(new MfaStateConflictException())
            .thenThrow(new MfaSecurityUnavailableException());

        performValid()
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("STEP_UP_INVALID"));

        performValid()
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MFA_STATE_CONFLICT"));

        performValid()
            .andExpect(status().isServiceUnavailable())
            .andExpect(
                jsonPath("$.code")
                    .value("MFA_SECURITY_UNAVAILABLE")
            );
    }

    @Test
    void shouldRedactGrantAndRecoveryCodesFromToString() {
        RotateMfaRecoveryCodesRequest request =
            new RotateMfaRecoveryCodesRequest("opaque-grant");

        RotateMfaRecoveryCodesResponse response =
            RotateMfaRecoveryCodesResponse.from(result());

        assertThat(request.toString())
            .doesNotContain("opaque-grant");

        assertThat(response.toString())
            .doesNotContain(
                "recovery-code-one",
                "recovery-code-two"
            );
    }

    private ResultActions performValid() throws Exception {
        return mockMvc.perform(post(ENDPOINT)
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

    private static RotateMfaRecoveryCodesResult result() {
        return new RotateMfaRecoveryCodesResult(
            List.of(
                "recovery-code-one",
                "recovery-code-two"
            )
        );
    }

    private void mockJwt() {
        when(jwtDecoder.decode("token")).thenReturn(
            Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(USER_ID.toString())
                .claim("role", "USER")
                .issuedAt(Instant.parse("2026-08-10T09:55:00Z"))
                .expiresAt(Instant.parse("2026-08-10T10:15:00Z"))
                .build()
        );
    }
}
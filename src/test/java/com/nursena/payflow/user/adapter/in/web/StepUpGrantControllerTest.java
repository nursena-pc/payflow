package com.nursena.payflow.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.nursena.payflow.configuration.SecurityConfiguration;
import com.nursena.payflow.observability.adapter.in.web.RequestCorrelationConfiguration;
import com.nursena.payflow.user.application.exception.MfaSecurityUnavailableException;
import com.nursena.payflow.user.application.port.in.IssueStepUpGrantCommand;
import com.nursena.payflow.user.application.port.in.IssueStepUpGrantResult;
import com.nursena.payflow.user.application.port.in.IssueStepUpGrantUseCase;
import com.nursena.payflow.user.domain.exception.InvalidStepUpGrantException;
import com.nursena.payflow.user.domain.exception.InvalidStepUpPurposeException;
import com.nursena.payflow.user.domain.exception.MfaStateConflictException;
import com.nursena.payflow.user.domain.exception.MfaVerificationFailedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StepUpGrantController.class)
@Import({
    RequestCorrelationConfiguration.class,
    SecurityConfiguration.class,
    StepUpExceptionHandler.class
})
class StepUpGrantControllerTest {

    private static final String SUBJECT =
        "10000000-0000-0000-0000-000000000105";

    @Autowired MockMvc mockMvc;
    @MockitoBean IssueStepUpGrantUseCase useCase;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void shouldRequireBearerAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/step-up/grants")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"mfa-disable\",\"code\":\"123456\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldIssueRedactedPurposeBoundGrantForAuthenticatedSubject() throws Exception {
        mockJwt();
        when(useCase.issue(any(IssueStepUpGrantCommand.class)))
            .thenReturn(new IssueStepUpGrantResult(
                "opaque-grant",
                "mfa-disable",
                Instant.parse("2026-08-10T10:05:00Z")
            ));

        mockMvc.perform(post("/api/v1/users/me/step-up/grants")
                .header(AUTHORIZATION, "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"mfa-disable\",\"code\":\"123456\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.grantToken").value("opaque-grant"))
            .andExpect(jsonPath("$.purpose").value("mfa-disable"));

        verify(useCase).issue(argThat(command ->
            command.subjectId().toString().equals(SUBJECT)
                && command.purpose().equals("mfa-disable")
                && command.code().equals("123456")
        ));
    }

    @Test
    void shouldUseStableBadRequestForInvalidPurpose() throws Exception {
        mockJwt();
        when(useCase.issue(any())).thenThrow(new InvalidStepUpPurposeException());
        mockMvc.perform(post("/api/v1/users/me/step-up/grants")
                .header(AUTHORIZATION, "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"unknown\",\"code\":\"123456\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldUseFrozenUnauthorizedContractForInvalidSecondFactor() throws Exception {
        mockJwt();
        when(useCase.issue(any())).thenThrow(new MfaVerificationFailedException());
        mockMvc.perform(post("/api/v1/users/me/step-up/grants")
                .header(AUTHORIZATION, "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"mfa-disable\",\"code\":\"000000\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("MFA_VERIFICATION_FAILED"));
    }

    @Test
    void shouldExposeOnlyCoarseForbiddenConflictAndUnavailableContracts() throws Exception {
        mockJwt();
        when(useCase.issue(any()))
            .thenThrow(new InvalidStepUpGrantException())
            .thenThrow(new MfaStateConflictException())
            .thenThrow(new MfaSecurityUnavailableException());

        performValid().andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("STEP_UP_INVALID"));

        performValid().andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MFA_STATE_CONFLICT"));

        performValid().andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("MFA_SECURITY_UNAVAILABLE"));
    }

    @Test
    void shouldRedactSecondFactorAndGrantFromToString() {
        IssueStepUpGrantRequest request =
            new IssueStepUpGrantRequest("mfa-disable", "123456");
        StepUpGrantResponse response = new StepUpGrantResponse(
            "opaque-grant", "mfa-disable", Instant.parse("2026-08-10T10:05:00Z")
        );
        assertThat(request.toString()).doesNotContain("123456");
        assertThat(response.toString()).doesNotContain("opaque-grant");
    }

    private org.springframework.test.web.servlet.ResultActions performValid() throws Exception {
        return mockMvc.perform(post("/api/v1/users/me/step-up/grants")
            .header(AUTHORIZATION, "Bearer token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"purpose\":\"mfa-disable\",\"code\":\"123456\"}"));
    }

    private void mockJwt() {
        when(jwtDecoder.decode("token")).thenReturn(
            Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(SUBJECT)
                .claim("role", "USER")
                .issuedAt(Instant.parse("2026-08-10T09:55:00Z"))
                .expiresAt(Instant.parse("2026-08-10T10:15:00Z"))
                .build()
        );
    }
}

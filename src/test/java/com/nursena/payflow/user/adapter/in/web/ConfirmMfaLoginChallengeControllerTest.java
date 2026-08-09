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

import com.nursena.payflow.observability.adapter.in.web.RequestCorrelationConfiguration;
import com.nursena.payflow.configuration.SecurityConfiguration;
import com.nursena.payflow.user.application.exception.MfaSecurityUnavailableException;
import com.nursena.payflow.user.application.port.in.AuthenticatedUserResult;
import com.nursena.payflow.user.application.port.in.ConfirmMfaLoginChallengeCommand;
import com.nursena.payflow.user.application.port.in.ConfirmMfaLoginChallengeUseCase;
import com.nursena.payflow.user.domain.exception.InvalidMfaLoginChallengeException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ConfirmMfaLoginChallengeController.class)
@Import({
    RequestCorrelationConfiguration.class,
    SecurityConfiguration.class,
    UserAuthenticationExceptionHandler.class
})
class ConfirmMfaLoginChallengeControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ConfirmMfaLoginChallengeUseCase useCase;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void shouldIssueCredentialsOnlyAfterChallengeConfirmation() throws Exception {
        when(useCase.confirm(any(ConfirmMfaLoginChallengeCommand.class)))
            .thenReturn(new AuthenticatedUserResult(
                "access",
                Instant.parse("2026-08-08T12:15:00Z"),
                "refresh",
                Instant.parse("2026-08-15T12:00:00Z")
            ));

        mockMvc.perform(post("/api/v1/auth/mfa/challenges/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeToken\":\"challenge\",\"code\":\"123456\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("access"))
            .andExpect(jsonPath("$.refreshToken").value("refresh"));

        verify(useCase).confirm(argThat(command ->
            command.challengeToken().equals("challenge")
                && command.code().equals("123456")
        ));
    }

    @Test
    void shouldReturnGenericUnauthorizedForInvalidChallengeOrProof() throws Exception {
        when(useCase.confirm(any(ConfirmMfaLoginChallengeCommand.class)))
            .thenThrow(new InvalidMfaLoginChallengeException());

        mockMvc.perform(post("/api/v1/auth/mfa/challenges/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeToken\":\"unknown\",\"code\":\"000000\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("MFA_CHALLENGE_INVALID"))
            .andExpect(jsonPath("$.message").value(
                "The MFA challenge or proof could not be verified."
            ));
    }


    @Test
    void shouldPassRecoveryCodeThroughSameConfirmationCommand() throws Exception {
        when(useCase.confirm(any(ConfirmMfaLoginChallengeCommand.class)))
            .thenReturn(new AuthenticatedUserResult(
                "access",
                Instant.parse("2026-08-09T12:15:00Z"),
                "refresh",
                Instant.parse("2026-08-16T12:00:00Z")
            ));

        String recoveryCode = "AbCdEfGhIjKlMnOpQrStUv";

        mockMvc.perform(post("/api/v1/auth/mfa/challenges/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"challengeToken\":\"challenge\",\"code\":\""
                        + recoveryCode
                        + "\"}"
                ))
            .andExpect(status().isOk());

        verify(useCase).confirm(argThat(command ->
            command.challengeToken().equals("challenge")
                && command.code().equals(recoveryCode)
        ));
    }

    @Test
    void shouldUseSameUnauthorizedContractForMalformedValues() throws Exception {
        when(useCase.confirm(any(ConfirmMfaLoginChallengeCommand.class)))
            .thenThrow(new InvalidMfaLoginChallengeException());

        mockMvc.perform(post("/api/v1/auth/mfa/challenges/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeToken\":\"\",\"code\":\"x\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("MFA_CHALLENGE_INVALID"));
    }

    @Test
    void shouldUseGenericUnauthorizedContractWhenRequestBodyIsMissing() throws Exception {
        when(useCase.confirm(any(ConfirmMfaLoginChallengeCommand.class)))
            .thenThrow(new InvalidMfaLoginChallengeException());

        mockMvc.perform(post("/api/v1/auth/mfa/challenges/confirm")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("MFA_CHALLENGE_INVALID"));

        verify(useCase).confirm(argThat(command ->
            command.challengeToken() == null && command.code() == null
        ));
    }

    @Test
    void shouldReturnServiceUnavailableWhenSecretProtectionFails() throws Exception {
        when(useCase.confirm(any(ConfirmMfaLoginChallengeCommand.class)))
            .thenThrow(new MfaSecurityUnavailableException());

        mockMvc.perform(post("/api/v1/auth/mfa/challenges/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeToken\":\"challenge\",\"code\":\"123456\"}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("MFA_SECURITY_UNAVAILABLE"));
    }

    @Test
    void shouldRedactRequestAndChallengeResponseToString() {
        ConfirmMfaLoginChallengeRequest request =
            new ConfirmMfaLoginChallengeRequest("secret-challenge", "123456");
        MfaChallengeRequiredResponse response = new MfaChallengeRequiredResponse(
            "MFA_REQUIRED",
            "secret-challenge",
            Instant.parse("2026-08-08T12:05:00Z")
        );
        assertThat(request.toString()).doesNotContain("secret-challenge", "123456");
        assertThat(response.toString()).doesNotContain("secret-challenge");
    }
}

package com.nursena.payflow.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request
    .MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result
    .MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result
    .MockMvcResultMatchers.status;

import com.nursena.payflow.configuration
    .SecurityConfiguration;
import com.nursena.payflow.observability.adapter.in.web
    .RequestCorrelationConfiguration;
import com.nursena.payflow.user.application.port.in
    .ConfirmPasswordRecoveryCommand;
import com.nursena.payflow.user.application.port.in
    .ConfirmPasswordRecoveryUseCase;
import com.nursena.payflow.user.application.port.in
    .RequestPasswordRecoveryCommand;
import com.nursena.payflow.user.application.port.in
    .RequestPasswordRecoveryUseCase;
import com.nursena.payflow.user.domain.exception
    .InvalidAccountActionCredentialException;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation
    .Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet
    .WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito
    .MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PasswordRecoveryController.class)
@Import({
    RequestCorrelationConfiguration.class,
    SecurityConfiguration.class,
    PasswordRecoveryExceptionHandler.class
})
class PasswordRecoveryControllerTest {

    private static final String CREDENTIAL =
        "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";

    private static final String NEW_PASSWORD =
        "ReplacementPassword123!";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RequestPasswordRecoveryUseCase
        requestPasswordRecovery;

    @MockitoBean
    private ConfirmPasswordRecoveryUseCase
        confirmPasswordRecovery;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldAcceptRequestWithoutDisclosingEligibility()
        throws Exception {

        mockMvc.perform(
                post(
                    "/api/v1/auth/password-recovery/requests"
                )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "email": "Nursena@Example.COM"
                        }
                        """
                    )
            )
            .andExpect(status().isAccepted());

        verify(requestPasswordRecovery).request(
            argThat(command ->
                command.email().equals(
                    "Nursena@Example.COM"
                )
            )
        );
    }

    @Test
    void shouldRejectInvalidRequestEmail()
        throws Exception {

        mockMvc.perform(
                post(
                    "/api/v1/auth/password-recovery/requests"
                )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "email": "not-an-email"
                        }
                        """
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value("VALIDATION_FAILED")
            );
    }

    @Test
    void shouldConfirmRecoveryWithoutSecretEcho()
        throws Exception {

        mockMvc.perform(
                post(
                    "/api/v1/auth/password-recovery/confirm"
                )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "credential": "%s",
                          "newPassword": "%s"
                        }
                        """.formatted(
                            CREDENTIAL,
                            NEW_PASSWORD
                        )
                    )
            )
            .andExpect(status().isNoContent());

        verify(confirmPasswordRecovery).confirm(
            argThat(command ->
                command.credential().equals(CREDENTIAL)
                    && command.rawNewPassword()
                        .equals(NEW_PASSWORD)
            )
        );
    }

    @Test
    void shouldShareRegistrationPasswordLengthBounds()
        throws NoSuchMethodException {

        Size registrationPolicy =
            RegisterUserRequest.class
                .getDeclaredMethod("password")
                .getAnnotation(Size.class);
        Size recoveryPolicy =
            PasswordRecoveryConfirmRequest.class
                .getDeclaredMethod("newPassword")
                .getAnnotation(Size.class);

        assertThat(registrationPolicy).isNotNull();
        assertThat(recoveryPolicy).isNotNull();
        assertThat(recoveryPolicy.min())
            .isEqualTo(registrationPolicy.min())
            .isEqualTo(PasswordPolicy.MIN_LENGTH);
        assertThat(recoveryPolicy.max())
            .isEqualTo(registrationPolicy.max())
            .isEqualTo(PasswordPolicy.MAX_LENGTH);
    }

    @Test
    void shouldReuseRegistrationPasswordStrengthPolicy()
        throws Exception {

        mockMvc.perform(
                post(
                    "/api/v1/auth/password-recovery/confirm"
                )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "credential": "%s",
                          "newPassword": "short"
                        }
                        """.formatted(CREDENTIAL)
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value("VALIDATION_FAILED")
            )
            .andExpect(
                jsonPath("$.violations[0].field")
                    .value("newPassword")
            );
    }

    @Test
    void shouldReturnStableInvalidCredentialError()
        throws Exception {

        doThrow(
            new InvalidAccountActionCredentialException()
        )
            .when(confirmPasswordRecovery)
            .confirm(
                any(ConfirmPasswordRecoveryCommand.class)
            );

        mockMvc.perform(
                post(
                    "/api/v1/auth/password-recovery/confirm"
                )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "credential": "%s",
                          "newPassword": "%s"
                        }
                        """.formatted(
                            CREDENTIAL,
                            NEW_PASSWORD
                        )
                    )
            )
            .andExpect(
                status().isUnprocessableEntity()
            )
            .andExpect(
                jsonPath("$.code").value(
                    "ACCOUNT_ACTION_CREDENTIAL_INVALID"
                )
            )
            .andExpect(
                jsonPath("$.message").value(
                    "Account action credential is invalid."
                )
            );
    }

    @Test
    void shouldRedactIdentityCredentialAndPassword() {
        String email = "nursena@example.com";
        PasswordRecoveryRequest request =
            new PasswordRecoveryRequest(email);
        RequestPasswordRecoveryCommand requestCommand =
            new RequestPasswordRecoveryCommand(email);
        PasswordRecoveryConfirmRequest confirmRequest =
            new PasswordRecoveryConfirmRequest(
                CREDENTIAL,
                NEW_PASSWORD
            );
        ConfirmPasswordRecoveryCommand confirmCommand =
            new ConfirmPasswordRecoveryCommand(
                CREDENTIAL,
                NEW_PASSWORD
            );

        assertThat(request.toString())
            .doesNotContain(email);
        assertThat(requestCommand.toString())
            .doesNotContain(email);
        assertThat(confirmRequest.toString())
            .doesNotContain(CREDENTIAL, NEW_PASSWORD);
        assertThat(confirmCommand.toString())
            .doesNotContain(CREDENTIAL, NEW_PASSWORD);
    }
}

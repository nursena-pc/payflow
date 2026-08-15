package com.nursena.payflow.user.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request
    .MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result
    .MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result
    .MockMvcResultMatchers.status;

import com.nursena.payflow.clientcontext.adapter.in.web.ClientAddressResolver;
import com.nursena.payflow.clientcontext.domain.IpAddress;
import com.nursena.payflow.clientcontext.domain.ResolvedClientAddress;
import com.nursena.payflow.configuration
    .SecurityConfiguration;
import com.nursena.payflow.observability.adapter.in.web
    .RequestCorrelationConfiguration;
import com.nursena.payflow.user.application.port.in
    .ConfirmEmailVerificationCommand;
import com.nursena.payflow.user.application.port.in
    .ConfirmEmailVerificationUseCase;
import com.nursena.payflow.user.application.port.in
    .RequestEmailVerificationCommand;
import com.nursena.payflow.user.application.port.in
    .RequestEmailVerificationUseCase;
import com.nursena.payflow.user.domain.exception
    .InvalidAccountActionCredentialException;
import org.junit.jupiter.api.BeforeEach;
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

@WebMvcTest(EmailVerificationController.class)
@Import({
    RequestCorrelationConfiguration.class,
    SecurityConfiguration.class,
    EmailVerificationExceptionHandler.class
})
class EmailVerificationControllerTest {

    private static final String CREDENTIAL =
        "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RequestEmailVerificationUseCase
        requestEmailVerification;

    @MockitoBean
    private ConfirmEmailVerificationUseCase
        confirmEmailVerification;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientAddressResolver clientAddressResolver;

    @BeforeEach
    void setUpClientAddress() {
        ResolvedClientAddress resolved =
            mock(ResolvedClientAddress.class);
        when(resolved.address()).thenReturn(
            IpAddress.parse("203.0.113.10")
        );
        when(clientAddressResolver.resolve(any()))
            .thenReturn(resolved);
    }

    @Test
    void shouldAcceptRequestWithoutDisclosingEligibility()
        throws Exception {

        mockMvc.perform(
                post(
                    "/api/v1/auth/email-verification/requests"
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

        verify(requestEmailVerification).request(
            argThat(command ->
                command.email().equals(
                    "Nursena@Example.COM"
                )
                    && command.effectiveClientAddress().equals(
                        IpAddress.parse("203.0.113.10")
                    )
            )
        );
    }

    @Test
    void shouldRejectInvalidRequestEmail()
        throws Exception {

        mockMvc.perform(
                post(
                    "/api/v1/auth/email-verification/requests"
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
    void shouldConfirmEmailWithNoCredentialEcho()
        throws Exception {

        mockMvc.perform(
                post(
                    "/api/v1/auth/email-verification/confirm"
                )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "credential": "%s"
                        }
                        """.formatted(CREDENTIAL)
                    )
            )
            .andExpect(status().isNoContent());

        verify(confirmEmailVerification).confirm(
            argThat(command ->
                command.credential().equals(CREDENTIAL)
            )
        );
    }

    @Test
    void shouldReturnStableInvalidCredentialError()
        throws Exception {

        doThrow(
            new InvalidAccountActionCredentialException()
        )
            .when(confirmEmailVerification)
            .confirm(
                any(ConfirmEmailVerificationCommand.class)
            );

        mockMvc.perform(
                post(
                    "/api/v1/auth/email-verification/confirm"
                )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "credential": "%s"
                        }
                        """.formatted(CREDENTIAL)
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
    void shouldRedactEmailFromRequestAndCommandStrings() {
        String email = "nursena@example.com";
        EmailVerificationRequest request =
            new EmailVerificationRequest(email);
        RequestEmailVerificationCommand command =
            new RequestEmailVerificationCommand(
                email,
                IpAddress.parse("203.0.113.10")
            );

        assertThat(request.toString())
            .doesNotContain(email);
        assertThat(command.toString())
            .doesNotContain(email);
    }

    @Test
    void shouldRedactCredentialFromRequestAndCommandStrings() {
        EmailVerificationConfirmRequest request =
            new EmailVerificationConfirmRequest(
                CREDENTIAL
            );
        ConfirmEmailVerificationCommand command =
            new ConfirmEmailVerificationCommand(
                CREDENTIAL
            );

        assertThat(request.toString())
            .doesNotContain(CREDENTIAL);
        assertThat(command.toString())
            .doesNotContain(CREDENTIAL);
    }
}

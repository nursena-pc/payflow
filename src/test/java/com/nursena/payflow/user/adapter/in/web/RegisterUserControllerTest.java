package com.nursena.payflow.user.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.nursena.payflow.configuration.SecurityConfiguration;
import com.nursena.payflow.user.application.port.in.RegisterUserCommand;
import com.nursena.payflow.user.application.port.in.RegisterUserUseCase;
import com.nursena.payflow.user.domain.exception.EmailAlreadyRegisteredException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RegisterUserController.class)
@Import({
    SecurityConfiguration.class,
    UserRegistrationExceptionHandler.class
})
class RegisterUserControllerTest {

    private static final UUID USER_ID =
        UUID.fromString("0f65d80a-91fc-46f5-8782-4dbb32375b81");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterUserUseCase registerUserUseCase;

    @Test
    void shouldRegisterUser() throws Exception {
        when(registerUserUseCase.register(any(RegisterUserCommand.class)))
            .thenReturn(USER_ID);

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                  "email": "nursena@example.com",
                                  "password": "StrongPassword123!"
                                }
                                """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.userId")
                .value(USER_ID.toString()));

        verify(registerUserUseCase).register(argThat(command ->
            command.email().equals("nursena@example.com")
                && command.rawPassword()
                .equals("StrongPassword123!")
        ));
    }

    @Test
    void shouldRejectInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                  "email": "not-an-email",
                                  "password": "StrongPassword123!"
                                }
                                """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code")
                .value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.violations[0].field")
                .value("email"));
    }

    @Test
    void shouldRejectShortPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                  "email": "nursena@example.com",
                                  "password": "short"
                                }
                                """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code")
                .value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.violations[0].field")
                .value("password"));
    }

    @Test
    void shouldReturnConflictForRegisteredEmail() throws Exception {
        when(registerUserUseCase.register(any(RegisterUserCommand.class)))
            .thenThrow(new EmailAlreadyRegisteredException());

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                  "email": "nursena@example.com",
                                  "password": "StrongPassword123!"
                                }
                                """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code")
                .value("EMAIL_ALREADY_REGISTERED"))
            .andExpect(jsonPath("$.message")
                .value(
                    "A user with this email address already exists."
                ))
            .andExpect(jsonPath("$.path")
                .value("/api/v1/auth/register"));
    }
}

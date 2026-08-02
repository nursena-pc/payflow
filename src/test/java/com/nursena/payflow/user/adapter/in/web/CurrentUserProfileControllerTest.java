package com.nursena.payflow.user.adapter.in.web;

import com.nursena.payflow.observability.adapter.in.web.RequestCorrelationConfiguration;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.nursena.payflow.configuration.SecurityConfiguration;
import com.nursena.payflow.user.application.port.in.GetCurrentUserProfileResult;
import com.nursena.payflow.user.application.port.in.GetCurrentUserProfileUseCase;
import com.nursena.payflow.user.domain.exception.UserNotFoundException;
import com.nursena.payflow.user.domain.model.UserRole;
import com.nursena.payflow.user.domain.model.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CurrentUserProfileController.class)
@Import({
    RequestCorrelationConfiguration.class,
    SecurityConfiguration.class,
    CurrentUserProfileExceptionHandler.class
})
class CurrentUserProfileControllerTest {

    private static final UUID USER_ID =
        UUID.fromString(
            "8805681d-d537-42f2-8906-5da1f0666ab7"
        );

    private static final Instant CREATED_AT =
        Instant.parse("2026-07-12T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetCurrentUserProfileUseCase profileUseCase;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void shouldReturnCurrentUserProfile()
        throws Exception {

        when(profileUseCase.getProfile(USER_ID))
            .thenReturn(
                new GetCurrentUserProfileResult(
                    USER_ID,
                    "nursena@example.com",
                    UserRole.USER,
                    UserStatus.ACTIVE,
                    CREATED_AT
                )
            );

        mockMvc.perform(
                get("/api/v1/users/me")
                    .with(jwt().jwt(token ->
                        token.subject(
                            USER_ID.toString()
                        )
                    ))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id")
                .value(USER_ID.toString()))
            .andExpect(jsonPath("$.email")
                .value("nursena@example.com"))
            .andExpect(jsonPath("$.role")
                .value("USER"))
            .andExpect(jsonPath("$.status")
                .value("ACTIVE"))
            .andExpect(jsonPath("$.createdAt")
                .value(CREATED_AT.toString()));

        verify(profileUseCase)
            .getProfile(USER_ID);
    }

    @Test
    void shouldRejectRequestWithoutAccessToken()
        throws Exception {

        mockMvc.perform(
                get("/api/v1/users/me")
            )
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(profileUseCase);
    }

    @Test
    void shouldReturnNotFoundForUnknownUser()
        throws Exception {

        when(profileUseCase.getProfile(USER_ID))
            .thenThrow(
                new UserNotFoundException()
            );

        mockMvc.perform(
                get("/api/v1/users/me")
                    .with(jwt().jwt(token ->
                        token.subject(
                            USER_ID.toString()
                        )
                    ))
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code")
                .value("USER_NOT_FOUND"))
            .andExpect(jsonPath("$.message")
                .value("User could not be found."))
            .andExpect(jsonPath("$.path")
                .value("/api/v1/users/me"));

        verify(profileUseCase)
            .getProfile(USER_ID);
    }
}

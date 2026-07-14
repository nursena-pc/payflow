package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.nursena.payflow.user.application.port.in.GetCurrentUserProfileResult;
import com.nursena.payflow.user.application.port.out.UserRepositoryPort;
import com.nursena.payflow.user.domain.exception.UserNotFoundException;
import com.nursena.payflow.user.domain.model.EmailAddress;
import com.nursena.payflow.user.domain.model.User;
import com.nursena.payflow.user.domain.model.UserRole;
import com.nursena.payflow.user.domain.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetCurrentUserProfileServiceTest {

    private static final UUID USER_ID = UUID.fromString(
        "8805681d-d537-42f2-8906-5da1f0666ab7"
    );

    private static final Instant CREATED_AT =
        Instant.parse("2026-07-12T12:00:00Z");

    @Mock
    private UserRepositoryPort userRepository;

    private GetCurrentUserProfileService service;

    @BeforeEach
    void setUp() {
        service = new GetCurrentUserProfileService(
            userRepository
        );
    }

    @Test
    void shouldReturnCurrentUserProfile() {
        User user = activeUser();

        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(user));

        GetCurrentUserProfileResult result =
            service.getProfile(USER_ID);

        assertThat(result.id()).isEqualTo(USER_ID);
        assertThat(result.email())
            .isEqualTo("nursena@example.com");
        assertThat(result.role())
            .isEqualTo(UserRole.USER);
        assertThat(result.status())
            .isEqualTo(UserStatus.ACTIVE);
        assertThat(result.createdAt())
            .isEqualTo(CREATED_AT);

        verify(userRepository).findById(USER_ID);
    }

    @Test
    void shouldRejectUnknownUser() {
        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(
            () -> service.getProfile(USER_ID)
        )
            .isInstanceOf(UserNotFoundException.class)
            .hasMessage("User could not be found.");

        verify(userRepository).findById(USER_ID);
    }

    private static User activeUser() {
        return User.rehydrate(
            USER_ID,
            EmailAddress.of("nursena@example.com"),
            "$2a$12$hashed-password",
            UserRole.USER,
            UserStatus.ACTIVE,
            CREATED_AT,
            CREATED_AT
        );
    }
}

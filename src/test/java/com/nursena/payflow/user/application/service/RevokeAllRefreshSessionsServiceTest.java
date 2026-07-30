package com.nursena.payflow.user.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.nursena.payflow.user.application.port.in.RevokeAllRefreshSessionsCommand;
import com.nursena.payflow.user.application.port.out.RefreshTokenFamilyRepositoryPort;
import com.nursena.payflow.user.domain.model.RefreshTokenFamilyRevocationReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RevokeAllRefreshSessionsServiceTest {

    private static final UUID USER_ID =
        UUID.fromString(
            "95000000-0000-0000-0000-000000000002"
        );

    private static final Instant NOW =
        Instant.parse(
            "2026-07-30T10:15:30.123456789Z"
        );

    private static final Instant REVOKED_AT =
        Instant.parse(
            "2026-07-30T10:15:30.123456Z"
        );

    @Mock
    private RefreshTokenFamilyRepositoryPort
        familyRepository;

    private Clock clock;

    private RevokeAllRefreshSessionsService service;

    @BeforeEach
    void setUp() {
        clock =
            Clock.fixed(
                NOW,
                ZoneOffset.UTC
            );

        service =
            new RevokeAllRefreshSessionsService(
                familyRepository,
                clock
            );
    }

    @Test
    void shouldRevokeEveryActiveFamilyForUser() {
        when(
            familyRepository
                .revokeAllActiveByUserId(
                    USER_ID,
                    REVOKED_AT,
                    RefreshTokenFamilyRevocationReason
                        .ALL_SESSIONS_LOGOUT
                )
        )
            .thenReturn(3);

        service.revoke(
            command()
        );

        verify(familyRepository)
            .revokeAllActiveByUserId(
                USER_ID,
                REVOKED_AT,
                RefreshTokenFamilyRevocationReason
                    .ALL_SESSIONS_LOGOUT
            );
    }

    @Test
    void shouldRemainIdempotentWhenNoActiveFamilyExists() {
        when(
            familyRepository
                .revokeAllActiveByUserId(
                    USER_ID,
                    REVOKED_AT,
                    RefreshTokenFamilyRevocationReason
                        .ALL_SESSIONS_LOGOUT
                )
        )
            .thenReturn(0);

        assertThatCode(
            () ->
                service.revoke(
                    command()
                )
        )
            .doesNotThrowAnyException();

        verify(familyRepository)
            .revokeAllActiveByUserId(
                USER_ID,
                REVOKED_AT,
                RefreshTokenFamilyRevocationReason
                    .ALL_SESSIONS_LOGOUT
            );
    }

    @Test
    void shouldRejectNullCommandWithoutPersistenceAccess() {
        assertThatThrownBy(
            () ->
                service.revoke(null)
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "command must not be null"
            );

        verifyNoInteractions(
            familyRepository
        );
    }

    @Test
    void shouldRejectNullRepository() {
        assertThatThrownBy(
            () ->
                new RevokeAllRefreshSessionsService(
                    null,
                    clock
                )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "familyRepository must not be null"
            );
    }

    @Test
    void shouldRejectNullClock() {
        assertThatThrownBy(
            () ->
                new RevokeAllRefreshSessionsService(
                    familyRepository,
                    null
                )
        )
            .isInstanceOf(
                NullPointerException.class
            )
            .hasMessage(
                "clock must not be null"
            );
    }

    private static RevokeAllRefreshSessionsCommand
    command() {
        return new RevokeAllRefreshSessionsCommand(
            USER_ID
        );
    }
}

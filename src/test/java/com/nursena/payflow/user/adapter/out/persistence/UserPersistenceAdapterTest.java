package com.nursena.payflow.user.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Optional;
import java.sql.SQLException;
import java.time.Instant;

import com.nursena.payflow.user.domain.exception.EmailAlreadyRegisteredException;
import com.nursena.payflow.user.domain.model.EmailAddress;
import com.nursena.payflow.user.domain.model.User;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class UserPersistenceAdapterTest {

    private static final Instant NOW =
        Instant.parse("2026-07-12T12:00:00Z");

    @Mock
    private SpringDataUserRepository repository;

    private UserPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new UserPersistenceAdapter(repository);
    }

    @Test
    void shouldCheckExistenceUsingNormalizedEmail() {
        EmailAddress email = EmailAddress.of("NURSENA@example.com");

        when(repository.existsByEmail("nursena@example.com"))
            .thenReturn(true);

        boolean exists = adapter.existsByEmail(email);

        assertThat(exists).isTrue();
        verify(repository).existsByEmail("nursena@example.com");
    }

    @Test
    void shouldSaveAndRestoreUser() {
        User user = User.register(
            EmailAddress.of("nursena@example.com"),
            "$2a$12$hashed-password",
            NOW
        );

        Instant verifiedAt = NOW.plusSeconds(60);
        user.verifyEmail(verifiedAt);

        when(repository.saveAndFlush(any(UserJpaEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        User savedUser = adapter.save(user);

        assertThat(savedUser.id()).isEqualTo(user.id());
        assertThat(savedUser.email()).isEqualTo(user.email());
        assertThat(savedUser.passwordHash())
            .isEqualTo(user.passwordHash());
        assertThat(savedUser.role()).isEqualTo(user.role());
        assertThat(savedUser.status()).isEqualTo(user.status());
        assertThat(savedUser.emailVerifiedAt())
            .isEqualTo(verifiedAt);
        assertThat(savedUser.createdAt()).isEqualTo(NOW);
        assertThat(savedUser.updatedAt()).isEqualTo(verifiedAt);
    }

    @Test
    void shouldFindUserByNormalizedEmail() {
        User user = User.register(
            EmailAddress.of("nursena@example.com"),
            "$2a$12$hashed-password",
            NOW
        );

        Instant verifiedAt = NOW.plusSeconds(60);
        user.verifyEmail(verifiedAt);

        UserJpaEntity entity = new UserJpaEntity(
            user.id(),
            user.email().value(),
            user.passwordHash(),
            user.role(),
            user.status(),
            user.emailVerifiedAt(),
            user.createdAt(),
            user.updatedAt()
        );

        when(repository.findByEmail("nursena@example.com"))
            .thenReturn(Optional.of(entity));

        Optional<User> result = adapter.findByEmail(
            EmailAddress.of("NURSENA@example.com")
        );

        assertThat(result).isPresent();

        User foundUser = result.orElseThrow();

        assertThat(foundUser.id()).isEqualTo(user.id());
        assertThat(foundUser.email()).isEqualTo(user.email());
        assertThat(foundUser.passwordHash())
            .isEqualTo(user.passwordHash());
        assertThat(foundUser.role()).isEqualTo(user.role());
        assertThat(foundUser.status()).isEqualTo(user.status());
        assertThat(foundUser.emailVerifiedAt())
            .isEqualTo(verifiedAt);
        assertThat(foundUser.createdAt())
            .isEqualTo(user.createdAt());
        assertThat(foundUser.updatedAt())
            .isEqualTo(user.updatedAt());

        verify(repository)
            .findByEmail("nursena@example.com");
    }

    @Test
    void shouldTranslateDuplicateEmailConstraintViolation() {
        User user = User.register(
            EmailAddress.of("nursena@example.com"),
            "$2a$12$hashed-password",
            NOW
        );

        ConstraintViolationException constraintViolation =
            new ConstraintViolationException(
                "duplicate email",
                new SQLException(),
                "uq_users_email_lower"
            );

        when(repository.saveAndFlush(any(UserJpaEntity.class)))
            .thenThrow(new DataIntegrityViolationException(
                "duplicate email",
                constraintViolation
            ));

        assertThatThrownBy(() -> adapter.save(user))
            .isInstanceOf(EmailAlreadyRegisteredException.class)
            .hasMessage(
                "A user with this email address already exists."
            );
    }

    @Test
    void shouldFindUserById() {
        User user = User.register(
            EmailAddress.of("nursena@example.com"),
            "$2a$12$hashed-password",
            NOW
        );

        UserJpaEntity entity = new UserJpaEntity(
            user.id(),
            user.email().value(),
            user.passwordHash(),
            user.role(),
            user.status(),
            user.emailVerifiedAt(),
            user.createdAt(),
            user.updatedAt()
        );

        when(repository.findById(user.id()))
            .thenReturn(Optional.of(entity));

        Optional<User> result =
            adapter.findById(user.id());

        assertThat(result).isPresent();

        User foundUser = result.orElseThrow();

        assertThat(foundUser.id()).isEqualTo(user.id());
        assertThat(foundUser.email())
            .isEqualTo(user.email());
        assertThat(foundUser.role())
            .isEqualTo(user.role());
        assertThat(foundUser.status())
            .isEqualTo(user.status());
        assertThat(foundUser.createdAt())
            .isEqualTo(user.createdAt());

        verify(repository).findById(user.id());
    }
}

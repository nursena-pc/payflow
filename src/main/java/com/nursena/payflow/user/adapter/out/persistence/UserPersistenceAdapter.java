package com.nursena.payflow.user.adapter.out.persistence;

import com.nursena.payflow.user.application.port.out.UserRepositoryPort;
import com.nursena.payflow.user.domain.exception.EmailAlreadyRegisteredException;
import com.nursena.payflow.user.domain.model.EmailAddress;
import com.nursena.payflow.user.domain.model.User;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import java.util.Optional;
import java.util.UUID;

@Component
class UserPersistenceAdapter implements UserRepositoryPort {

    private static final String EMAIL_CONSTRAINT = "users_email_key";
    private static final String CASE_INSENSITIVE_EMAIL_CONSTRAINT =
        "uq_users_email_lower";

    private final SpringDataUserRepository repository;

    UserPersistenceAdapter(SpringDataUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsByEmail(EmailAddress email) {
        return repository.existsByEmail(email.value());
    }

    @Override
    public Optional<User> findByEmail(EmailAddress email) {
        return repository.findByEmail(email.value())
            .map(UserPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<User> findById(UUID userId) {
        return repository.findById(userId)
            .map(UserPersistenceAdapter::toDomain);
    }

    @Override
    public User save(User user) {
        UserJpaEntity entity = toEntity(user);

        try {
            UserJpaEntity saved = repository.saveAndFlush(entity);
            return toDomain(saved);
        } catch (DataIntegrityViolationException exception) {
            if (isEmailConstraintViolation(exception)) {
                throw new EmailAlreadyRegisteredException();
            }

            throw exception;
        }
    }

    private static UserJpaEntity toEntity(User user) {
        return new UserJpaEntity(
            user.id(),
            user.email().value(),
            user.passwordHash(),
            user.role(),
            user.status(),
            user.emailVerifiedAt(),
            user.createdAt(),
            user.updatedAt()
        );
    }

    private static User toDomain(UserJpaEntity entity) {
        return User.rehydrate(
            entity.getId(),
            EmailAddress.of(entity.getEmail()),
            entity.getPasswordHash(),
            entity.getRole(),
            entity.getStatus(),
            entity.getEmailVerifiedAt(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private static boolean isEmailConstraintViolation(
        DataIntegrityViolationException exception
    ) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation) {
                String constraintName = violation.getConstraintName();

                return EMAIL_CONSTRAINT.equals(constraintName)
                    || CASE_INSENSITIVE_EMAIL_CONSTRAINT.equals(
                    constraintName
                );
            }

            cause = cause.getCause();
        }

        return false;
    }
}

package com.nursena.payflow.user.application.service;

import java.time.Clock;
import java.util.UUID;

import com.nursena.payflow.user.application.port.in.RegisterUserCommand;
import com.nursena.payflow.user.application.port.in.RegisterUserUseCase;
import com.nursena.payflow.user.application.port.out.PasswordHashingPort;
import com.nursena.payflow.user.application.port.out.UserRepositoryPort;
import com.nursena.payflow.user.domain.exception.EmailAlreadyRegisteredException;
import com.nursena.payflow.user.domain.model.EmailAddress;
import com.nursena.payflow.user.domain.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterUserService implements RegisterUserUseCase {

    private final UserRepositoryPort userRepository;
    private final PasswordHashingPort passwordHashing;
    private final Clock clock;

    public RegisterUserService(
        UserRepositoryPort userRepository,
        PasswordHashingPort passwordHashing,
        Clock clock
    ) {
        this.userRepository = userRepository;
        this.passwordHashing = passwordHashing;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UUID register(RegisterUserCommand command) {
        EmailAddress email = EmailAddress.of(command.email());

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        String passwordHash = passwordHashing.hash(command.rawPassword());

        User user = User.register(
            email,
            passwordHash,
            clock.instant()
        );

        return userRepository.save(user).id();
    }
}

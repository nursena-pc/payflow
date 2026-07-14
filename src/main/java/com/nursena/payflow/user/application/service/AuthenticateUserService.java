package com.nursena.payflow.user.application.service;

import com.nursena.payflow.user.application.port.in.AuthenticateUserCommand;
import com.nursena.payflow.user.application.port.in.AuthenticateUserResult;
import com.nursena.payflow.user.application.port.in.AuthenticateUserUseCase;
import com.nursena.payflow.user.application.port.out.GeneratedAccessToken;
import com.nursena.payflow.user.application.port.out.PasswordVerificationPort;
import com.nursena.payflow.user.application.port.out.TokenGenerationPort;
import com.nursena.payflow.user.application.port.out.UserRepositoryPort;
import com.nursena.payflow.user.domain.exception.InvalidCredentialsException;
import com.nursena.payflow.user.domain.exception.UserAccountUnavailableException;
import com.nursena.payflow.user.domain.model.EmailAddress;
import com.nursena.payflow.user.domain.model.User;
import com.nursena.payflow.user.domain.model.UserStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

@Service
@Transactional(readOnly = true)
public class AuthenticateUserService
    implements AuthenticateUserUseCase {

    private final UserRepositoryPort userRepository;
    private final PasswordVerificationPort passwordVerification;
    private final TokenGenerationPort tokenGeneration;

    public AuthenticateUserService(
        UserRepositoryPort userRepository,
        PasswordVerificationPort passwordVerification,
        TokenGenerationPort tokenGeneration
    ) {
        this.userRepository = userRepository;
        this.passwordVerification = passwordVerification;
        this.tokenGeneration = tokenGeneration;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthenticateUserResult authenticate(
        AuthenticateUserCommand command
    ) {
        EmailAddress email = EmailAddress.of(command.email());

        User user = userRepository.findByEmail(email)
            .orElseThrow(InvalidCredentialsException::new);

        boolean passwordMatches = passwordVerification.matches(
            command.rawPassword(),
            user.passwordHash()
        );

        if (!passwordMatches) {
            throw new InvalidCredentialsException();
        }

        if (user.status() != UserStatus.ACTIVE) {
            throw new UserAccountUnavailableException();
        }

        GeneratedAccessToken token = tokenGeneration.generate(user);

        return new AuthenticateUserResult(
            token.value(),
            token.expiresAt()
        );
    }
}

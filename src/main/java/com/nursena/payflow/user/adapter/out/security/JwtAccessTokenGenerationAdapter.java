package com.nursena.payflow.user.adapter.out.security;

import java.time.Clock;
import java.time.Instant;

import com.nursena.payflow.user.application.port.out.GeneratedAccessToken;
import com.nursena.payflow.user.application.port.out.AccessTokenGenerationPort;
import com.nursena.payflow.user.domain.model.User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
class JwtAccessTokenGenerationAdapter
    implements AccessTokenGenerationPort {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final Clock clock;

    JwtAccessTokenGenerationAdapter(
        JwtEncoder jwtEncoder,
        JwtProperties properties,
        Clock clock
    ) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public GeneratedAccessToken generate(User user) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(
            properties.accessTokenTtl()
        );

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .subject(user.id().toString())
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .claim(
                "email",
                user.email().value()
            )
            .claim(
                "role",
                user.role().name()
            )
            .build();

        Jwt jwt = jwtEncoder.encode(
            JwtEncoderParameters.from(claims)
        );

        return new GeneratedAccessToken(
            jwt.getTokenValue(),
            expiresAt
        );
    }
}

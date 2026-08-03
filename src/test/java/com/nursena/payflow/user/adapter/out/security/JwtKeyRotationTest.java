package com.nursena.payflow.user.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;

class JwtKeyRotationTest {

    private static final String ISSUER =
        "https://api.payflow.local";

    private final JwtConfiguration configuration =
        new JwtConfiguration();

    private JwtSigningKey activeKey;
    private JwtSigningKey previousKey;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() throws Exception {
        activeKey = signingKey(
            "active-2026-08"
        );

        previousKey = signingKey(
            "previous-2026-07"
        );

        JwtKeyRing keyRing = new JwtKeyRing(
            activeKey,
            List.of(
                activeKey.verificationKey(),
                previousKey.verificationKey()
            )
        );

        decoder = configuration.jwtDecoder(
            keyRing,
            new JwtProperties(
                ISSUER,
                Duration.ofMinutes(15)
            )
        );
    }

    @Test
    void shouldIssueWithActiveKidAndVerifyBothKeys() {
        JwtKeyRing keyRing = new JwtKeyRing(
            activeKey,
            List.of(
                activeKey.verificationKey(),
                previousKey.verificationKey()
            )
        );

        Jwt activeToken = configuration
            .jwtEncoder(keyRing)
            .encode(parameters());

        Jwt previousToken = encoder(
            previousKey,
            true
        ).encode(parameters());

        assertThat(activeToken.getHeaders())
            .containsEntry(
                "kid",
                "active-2026-08"
            );

        assertThat(
            activeToken.getHeaders().get("alg")
        )
            .hasToString("RS256");

        assertThat(
            decoder.decode(
                activeToken.getTokenValue()
            ).getSubject()
        )
            .isEqualTo("user-123");

        assertThat(
            decoder.decode(
                previousToken.getTokenValue()
            ).getSubject()
        )
            .isEqualTo("user-123");
    }

    @Test
    void shouldRejectUnknownKid() throws Exception {
        Jwt unknownToken = encoder(
            signingKey("unknown-2026-06"),
            true
        ).encode(parameters());

        assertThatThrownBy(
            () -> decoder.decode(
                unknownToken.getTokenValue()
            )
        )
            .isInstanceOf(JwtException.class);
    }

    @Test
    void shouldRejectMissingKid() {
        Jwt tokenWithoutKid = encoder(
            activeKey,
            false
        ).encode(parameters());

        assertThat(tokenWithoutKid.getHeaders())
            .doesNotContainKey("kid");

        assertThatThrownBy(
            () -> decoder.decode(
                tokenWithoutKid.getTokenValue()
            )
        )
            .isInstanceOf(JwtException.class);
    }

    @Test
    void shouldRejectUnapprovedSigningAlgorithm() {
        RSAKey rsaKey = new RSAKey.Builder(
            activeKey.publicKey()
        )
            .privateKey(activeKey.privateKey())
            .keyID(activeKey.keyId().value())
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS512)
            .build();

        JwtEncoder encoder = new NimbusJwtEncoder(
            new ImmutableJWKSet<>(
                new JWKSet(rsaKey)
            )
        );

        JwsHeader header = JwsHeader
            .with(SignatureAlgorithm.RS512)
            .keyId(activeKey.keyId().value())
            .build();

        Jwt token = encoder.encode(
            JwtEncoderParameters.from(
                header,
                parameters().getClaims()
            )
        );

        assertThatThrownBy(
            () -> decoder.decode(token.getTokenValue())
        )
            .isInstanceOf(JwtException.class);
    }

    private static JwtEncoder encoder(
        JwtSigningKey key,
        boolean includeKeyId
    ) {
        RSAKey.Builder builder = new RSAKey.Builder(
            key.publicKey()
        )
            .privateKey(key.privateKey())
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256);

        if (includeKeyId) {
            builder.keyID(key.keyId().value());
        }

        return new NimbusJwtEncoder(
            new ImmutableJWKSet<>(
                new JWKSet(builder.build())
            )
        );
    }

    private static JwtEncoderParameters parameters() {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject("user-123")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(900))
            .build();

        return JwtEncoderParameters.from(claims);
    }

    private static JwtSigningKey signingKey(
        String keyId
    ) throws Exception {
        KeyPairGenerator generator =
            KeyPairGenerator.getInstance("RSA");

        generator.initialize(2048);

        KeyPair keyPair = generator.generateKeyPair();

        return new JwtSigningKey(
            JwtKeyId.of(keyId),
            (RSAPublicKey) keyPair.getPublic(),
            (RSAPrivateKey) keyPair.getPrivate()
        );
    }
}

package com.nursena.payflow.user.adapter.out.security;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;

@Configuration
@Profile("!prod")
@EnableConfigurationProperties(JwtProperties.class)
class JwtConfiguration {

    private static final int RSA_KEY_SIZE = 2048;


    @Bean
    KeyPair jwtKeyPair() {
        try {
            KeyPairGenerator generator =
                KeyPairGenerator.getInstance("RSA");

            generator.initialize(RSA_KEY_SIZE);

            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                "RSA key generation is not available",
                exception
            );
        }
    }

    @Bean
    JwtEncoder jwtEncoder(KeyPair jwtKeyPair) {
        RSAPublicKey publicKey =
            (RSAPublicKey) jwtKeyPair.getPublic();

        RSAPrivateKey privateKey =
            (RSAPrivateKey) jwtKeyPair.getPrivate();

        RSAKey rsaKey = new RSAKey.Builder(publicKey)
            .privateKey(privateKey)
            .keyID(UUID.randomUUID().toString())
            .build();

        JWKSource<SecurityContext> jwkSource =
            new ImmutableJWKSet<>(
                new JWKSet(rsaKey)
            );

        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    JwtDecoder jwtDecoder(
        KeyPair jwtKeyPair,
        JwtProperties properties
    ) {
        RSAPublicKey publicKey =
            (RSAPublicKey) jwtKeyPair.getPublic();

        NimbusJwtDecoder decoder = NimbusJwtDecoder
            .withPublicKey(publicKey)
            .build();

        decoder.setJwtValidator(
            JwtValidators.createDefaultWithIssuer(
                properties.issuer()
            )
        );

        return decoder;
    }
}

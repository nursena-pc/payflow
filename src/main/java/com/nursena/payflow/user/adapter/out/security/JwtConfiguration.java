package com.nursena.payflow.user.adapter.out.security;

import java.util.List;
import java.util.Set;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ResourceLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;

@Configuration
@EnableConfigurationProperties({
    JwtProperties.class,
    JwtKeySetProperties.class
})
class JwtConfiguration {

    @Bean
    JwtKeyProvider jwtKeyProvider(
        JwtKeySetProperties properties,
        ResourceLoader resourceLoader
    ) {
        return switch (properties.providerMode()) {
            case EPHEMERAL ->
                new EphemeralJwtKeyProvider(
                    properties.activeKeyId()
                );
            case CONFIGURED ->
                new ConfiguredJwtKeyProvider(
                    properties,
                    resourceLoader
                );
        };
    }

    @Bean
    JwtKeyRing jwtKeyRing(
        JwtKeyProvider keyProvider
    ) {
        return keyProvider.load();
    }

    @Bean
    JwtEncoder jwtEncoder(JwtKeyRing keyRing) {
        JwtSigningKey activeKey =
            keyRing.activeSigningKey();

        RSAKey rsaKey = new RSAKey.Builder(
            activeKey.publicKey()
        )
            .privateKey(activeKey.privateKey())
            .keyID(activeKey.keyId().value())
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .build();

        JWKSource<SecurityContext> jwkSource =
            new ImmutableJWKSet<>(
                new JWKSet(rsaKey)
            );

        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    JwtDecoder jwtDecoder(
        JwtKeyRing keyRing,
        JwtProperties properties
    ) {
        List<JWK> verificationKeys =
            keyRing.verificationKeys()
                .stream()
                .map(key -> (JWK) verificationJwk(key))
                .toList();

        JWKSource<SecurityContext> delegate =
            new ImmutableJWKSet<>(
                new JWKSet(verificationKeys)
            );

        Set<String> trustedKeyIds =
            keyRing.verificationKeyIds();

        JWKSource<SecurityContext> strictKeySource =
            (selector, context) -> {
                Set<String> requestedKeyIds =
                    selector
                        .getMatcher()
                        .getKeyIDs();

                if (
                    requestedKeyIds == null
                        || requestedKeyIds.size() != 1
                        || !trustedKeyIds.containsAll(
                            requestedKeyIds
                        )
                ) {
                    return List.of();
                }

                return delegate.get(
                    selector,
                    context
                );
            };

        DefaultJWTProcessor<SecurityContext> processor =
            new DefaultJWTProcessor<>();

        processor.setJWSTypeVerifier(
            new DefaultJOSEObjectTypeVerifier<>(
                JOSEObjectType.JWT,
                null
            )
        );

        processor.setJWSKeySelector(
            new JWSVerificationKeySelector<>(
                JWSAlgorithm.RS256,
                strictKeySource
            )
        );

        processor.setJWTClaimsSetVerifier(
            (claims, context) -> {
                // Spring Security validates claims below.
            }
        );

        NimbusJwtDecoder decoder =
            new NimbusJwtDecoder(processor);

        decoder.setJwtValidator(
            JwtValidators.createDefaultWithIssuer(
                properties.issuer()
            )
        );

        return decoder;
    }

    private static RSAKey verificationJwk(
        JwtVerificationKey key
    ) {
        return new RSAKey.Builder(key.publicKey())
            .keyID(key.keyId().value())
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .build();
    }
}

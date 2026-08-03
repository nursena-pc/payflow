package com.nursena.payflow.user.adapter.out.security;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;

final class EphemeralJwtKeyProvider
    implements JwtKeyProvider {

    private static final int RSA_KEY_SIZE = 2048;

    private final JwtKeyId activeKeyId;

    EphemeralJwtKeyProvider(String activeKeyId) {
        this.activeKeyId = JwtKeyId.of(
            activeKeyId
        );
    }

    @Override
    public JwtKeyRing load() {
        KeyPair keyPair = generateKeyPair();

        JwtSigningKey activeKey = new JwtSigningKey(
            activeKeyId,
            (RSAPublicKey) keyPair.getPublic(),
            (RSAPrivateKey) keyPair.getPrivate()
        );

        return new JwtKeyRing(
            activeKey,
            List.of(activeKey.verificationKey())
        );
    }

    private static KeyPair generateKeyPair() {
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
}

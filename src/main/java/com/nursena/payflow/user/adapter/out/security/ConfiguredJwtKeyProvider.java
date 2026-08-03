package com.nursena.payflow.user.adapter.out.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

final class ConfiguredJwtKeyProvider
    implements JwtKeyProvider {

    private static final int MAXIMUM_PEM_BYTES = 16_384;

    private static final byte[] KEY_PAIR_PROBE =
        "payflow-jwt-key-pair-validation"
            .getBytes(StandardCharsets.US_ASCII);

    private final JwtKeySetProperties properties;
    private final ResourceLoader resourceLoader;

    ConfiguredJwtKeyProvider(
        JwtKeySetProperties properties,
        ResourceLoader resourceLoader
    ) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public JwtKeyRing load() {
        try {
            RSAPrivateKey privateKey = readPrivateKey(
                properties.activePrivateKeyLocation()
            );

            RSAPublicKey publicKey = readPublicKey(
                properties.activePublicKeyLocation(),
                "active"
            );

            verifyKeyPair(
                privateKey,
                publicKey
            );

            JwtSigningKey activeKey = new JwtSigningKey(
                JwtKeyId.of(properties.activeKeyId()),
                publicKey,
                privateKey
            );

            List<JwtVerificationKey> verificationKeys =
                new ArrayList<>();

            verificationKeys.add(
                activeKey.verificationKey()
            );

            if (properties.hasPreviousKey()) {
                verificationKeys.add(
                    new JwtVerificationKey(
                        JwtKeyId.of(
                            properties.previousKeyId()
                        ),
                        readPublicKey(
                            properties
                                .previousPublicKeyLocation(),
                            "previous"
                        )
                    )
                );
            }

            return new JwtKeyRing(
                activeKey,
                verificationKeys
            );
        } catch (
            IOException
                | GeneralSecurityException
                | IllegalArgumentException exception
        ) {
            throw new IllegalStateException(
                "JWT key-set configuration is invalid",
                exception
            );
        }
    }

    private RSAPrivateKey readPrivateKey(
        String location
    ) throws IOException, GeneralSecurityException {
        byte[] encoded = readPem(
            location,
            "PRIVATE KEY",
            "active private"
        );

        return (RSAPrivateKey) keyFactory()
            .generatePrivate(
                new PKCS8EncodedKeySpec(encoded)
            );
    }

    private RSAPublicKey readPublicKey(
        String location,
        String name
    ) throws IOException, GeneralSecurityException {
        byte[] encoded = readPem(
            location,
            "PUBLIC KEY",
            name + " public"
        );

        return (RSAPublicKey) keyFactory()
            .generatePublic(
                new X509EncodedKeySpec(encoded)
            );
    }

    private byte[] readPem(
        String location,
        String label,
        String name
    ) throws IOException {
        Resource resource =
            resourceLoader.getResource(location);

        if (!resource.exists() || !resource.isReadable()) {
            throw new IOException(
                "JWT " + name + " key resource is not readable"
            );
        }

        byte[] bytes;

        try (InputStream input = resource.getInputStream()) {
            bytes = input.readNBytes(
                MAXIMUM_PEM_BYTES + 1
            );
        }

        if (bytes.length > MAXIMUM_PEM_BYTES) {
            throw new IOException(
                "JWT " + name + " key resource is too large"
            );
        }

        String pem = new String(
            bytes,
            StandardCharsets.US_ASCII
        ).trim();

        String begin = "-----BEGIN " + label + "-----";
        String end = "-----END " + label + "-----";

        if (
            !pem.startsWith(begin)
                || !pem.endsWith(end)
        ) {
            throw new IOException(
                "JWT " + name + " key must use " + label
                    + " PEM encoding"
            );
        }

        String encoded = pem.substring(
            begin.length(),
            pem.length() - end.length()
        );

        if (encoded.contains("-----")) {
            throw new IOException(
                "JWT " + name + " key resource contains "
                    + "unexpected PEM content"
            );
        }

        try {
            String compact = encoded
                .replace("\r", "")
                .replace("\n", "")
                .replace("\t", "")
                .replace(" ", "");

            return Base64.getDecoder().decode(compact);
        } catch (IllegalArgumentException exception) {
            throw new IOException(
                "JWT " + name + " key contains invalid Base64",
                exception
            );
        }
    }

    private static void verifyKeyPair(
        RSAPrivateKey privateKey,
        RSAPublicKey publicKey
    ) throws GeneralSecurityException {
        Signature signer = Signature.getInstance(
            "SHA256withRSA"
        );

        signer.initSign(privateKey);
        signer.update(KEY_PAIR_PROBE);

        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance(
            "SHA256withRSA"
        );

        verifier.initVerify(publicKey);
        verifier.update(KEY_PAIR_PROBE);

        if (!verifier.verify(signature)) {
            throw new GeneralSecurityException(
                "JWT active public and private keys do not match"
            );
        }
    }

    private static KeyFactory keyFactory()
        throws GeneralSecurityException {

        return KeyFactory.getInstance("RSA");
    }
}

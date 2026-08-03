package com.nursena.payflow.user.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

class ConfiguredJwtKeyProviderTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldLoadActiveAndPreviousKeys()
        throws Exception {

        KeyPair active = generateKeyPair(2048);
        KeyPair previous = generateKeyPair(2048);

        JwtKeySetProperties properties = properties(
            active,
            previous
        );

        JwtKeyRing keyRing = provider(properties).load();

        assertThat(
            keyRing.activeSigningKey().keyId().value()
        )
            .isEqualTo("active-2026-08");

        assertThat(keyRing.verificationKeyIds())
            .containsExactlyInAnyOrder(
                "active-2026-08",
                "previous-2026-07"
            );
    }

    @Test
    void shouldRejectMismatchedActiveKeyPair()
        throws Exception {

        KeyPair privatePair = generateKeyPair(2048);
        KeyPair publicPair = generateKeyPair(2048);

        Path privateKey = writePrivateKey(
            "active-private.pem",
            privatePair
        );

        Path publicKey = writePublicKey(
            "active-public.pem",
            publicPair
        );

        JwtKeySetProperties properties =
            configuredProperties(
                privateKey,
                publicKey,
                null,
                null
            );

        assertThatThrownBy(
            () -> provider(properties).load()
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(
                "JWT key-set configuration is invalid"
            )
            .hasRootCauseMessage(
                "JWT active public and private keys do not match"
            );
    }

    @Test
    void shouldRejectWeakRsaKeys()
        throws Exception {

        KeyPair weak = generateKeyPair(1024);

        JwtKeySetProperties properties = properties(
            weak,
            null
        );

        assertThatThrownBy(
            () -> provider(properties).load()
        )
            .isInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage(
                "JWT RSA keys must be at least 2048 bits"
            );
    }

    @Test
    void shouldRejectUnreadableKeyResources() {
        JwtKeySetProperties properties =
            new JwtKeySetProperties(
                JwtKeyProviderMode.CONFIGURED,
                "active-2026-08",
                "file:/missing/active-private.pem",
                "file:/missing/active-public.pem",
                "",
                ""
            );

        assertThatThrownBy(
            () -> provider(properties).load()
        )
            .isInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage(
                "JWT active private key resource is not readable"
            );
    }

    @Test
    void shouldRejectUnsupportedPrivateKeyPemType()
        throws Exception {

        KeyPair active = generateKeyPair(2048);

        Path privateKey = temporaryDirectory.resolve(
            "active-private.pem"
        );

        Files.writeString(
            privateKey,
            pem("RSA PRIVATE KEY", active.getPrivate()),
            StandardCharsets.US_ASCII
        );

        Path publicKey = writePublicKey(
            "active-public.pem",
            active
        );

        JwtKeySetProperties properties =
            configuredProperties(
                privateKey,
                publicKey,
                null,
                null
            );

        assertThatThrownBy(
            () -> provider(properties).load()
        )
            .isInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage(
                "JWT active private key must use "
                    + "PRIVATE KEY PEM encoding"
            );
    }

    private JwtKeySetProperties properties(
        KeyPair active,
        KeyPair previous
    ) throws IOException {
        Path privateKey = writePrivateKey(
            "active-private.pem",
            active
        );

        Path publicKey = writePublicKey(
            "active-public.pem",
            active
        );

        Path previousPublicKey = null;

        if (previous != null) {
            previousPublicKey = writePublicKey(
                "previous-public.pem",
                previous
            );
        }

        return configuredProperties(
            privateKey,
            publicKey,
            previous == null
                ? null
                : "previous-2026-07",
            previousPublicKey
        );
    }

    private JwtKeySetProperties configuredProperties(
        Path privateKey,
        Path publicKey,
        String previousKeyId,
        Path previousPublicKey
    ) {
        return new JwtKeySetProperties(
            JwtKeyProviderMode.CONFIGURED,
            "active-2026-08",
            privateKey.toUri().toString(),
            publicKey.toUri().toString(),
            previousKeyId,
            previousPublicKey == null
                ? null
                : previousPublicKey.toUri().toString()
        );
    }

    private ConfiguredJwtKeyProvider provider(
        JwtKeySetProperties properties
    ) {
        return new ConfiguredJwtKeyProvider(
            properties,
            new DefaultResourceLoader()
        );
    }

    private Path writePrivateKey(
        String fileName,
        KeyPair keyPair
    ) throws IOException {
        Path path = temporaryDirectory.resolve(fileName);

        Files.writeString(
            path,
            pem("PRIVATE KEY", keyPair.getPrivate()),
            StandardCharsets.US_ASCII
        );

        return path;
    }

    private Path writePublicKey(
        String fileName,
        KeyPair keyPair
    ) throws IOException {
        Path path = temporaryDirectory.resolve(fileName);

        Files.writeString(
            path,
            pem("PUBLIC KEY", keyPair.getPublic()),
            StandardCharsets.US_ASCII
        );

        return path;
    }

    private static String pem(
        String label,
        Key key
    ) {
        String encoded = Base64
            .getMimeEncoder(
                64,
                new byte[] {'\n'}
            )
            .encodeToString(key.getEncoded());

        return "-----BEGIN " + label + "-----\n"
            + encoded
            + "\n-----END " + label + "-----\n";
    }

    private static KeyPair generateKeyPair(
        int keySize
    ) throws Exception {
        KeyPairGenerator generator =
            KeyPairGenerator.getInstance("RSA");

        generator.initialize(keySize);

        return generator.generateKeyPair();
    }
}

package com.nursena.payflow.maildelivery.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import com.nursena.payflow.maildelivery.domain.model.MailContentProtectionContext;
import com.nursena.payflow.maildelivery.domain.model.MailOutboxPurpose;
import com.nursena.payflow.maildelivery.domain.model.ProtectedMailContent;
import org.junit.jupiter.api.Test;

class AesGcmMailContentProtectionAdapterTest {

    @Test
    void shouldProtectWithFreshNoncesAndRejectTamperingOrContextSwap() {
        byte[] key = new byte[32];
        for (int index = 0; index < key.length; index++) {
            key[index] = (byte) index;
        }
        AesGcmMailContentProtectionAdapter adapter =
            new AesGcmMailContentProtectionAdapter(
                new SecretKeySpec(key, "AES"),
                new SecureRandom()
            );
        String plaintext =
            "https://app.payflow.local/verify-email?token=secret";
        MailContentProtectionContext context = context(
            "fda97dd3-2fb4-4538-8b11-c4c47fcb5303",
            "nursena@example.com"
        );

        ProtectedMailContent first = adapter.protect(context, plaintext);
        ProtectedMailContent second = adapter.protect(context, plaintext);

        assertThat(first).isNotEqualTo(second);
        assertThat(adapter.reveal(context, first)).isEqualTo(plaintext);
        assertThat(new String(first.value())).doesNotContain("secret");

        byte[] tampered = first.value();
        tampered[tampered.length - 1] ^= 1;
        assertThatThrownBy(() -> adapter.reveal(
            context,
            ProtectedMailContent.of(tampered)
        )).isInstanceOf(MailContentProtectionException.class);

        MailContentProtectionContext differentRecipient = context(
            "fda97dd3-2fb4-4538-8b11-c4c47fcb5303",
            "attacker@example.com"
        );
        assertThatThrownBy(() -> adapter.reveal(
            differentRecipient,
            first
        )).isInstanceOf(MailContentProtectionException.class);
    }

    private static MailContentProtectionContext context(
        String messageId,
        String recipient
    ) {
        return new MailContentProtectionContext(
            UUID.fromString(messageId),
            UUID.fromString("0a1fd9f5-88a8-4b80-8bb3-121bda9479cc"),
            MailOutboxPurpose.EMAIL_VERIFICATION,
            recipient,
            "Verify your PayFlow email"
        );
    }
}

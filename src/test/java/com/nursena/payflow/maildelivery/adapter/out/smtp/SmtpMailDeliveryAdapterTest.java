package com.nursena.payflow.maildelivery.adapter.out.smtp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

import com.nursena.payflow.maildelivery.application.port.out.MailContentProtectionPort;
import com.nursena.payflow.maildelivery.domain.model.MailOutboxMessage;
import com.nursena.payflow.maildelivery.domain.model.MailOutboxPurpose;
import com.nursena.payflow.maildelivery.domain.model.ProtectedMailContent;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class SmtpMailDeliveryAdapterTest {

    @Mock
    private JavaMailSender mailSender;
    @Mock
    private MailContentProtectionPort contentProtection;

    @Test
    void shouldBuildSmtpMessageWithoutLoggingOrPersistingPlaintext() throws Exception {
        MimeMessage mimeMessage = new MimeMessage(
            Session.getInstance(new Properties())
        );
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        ProtectedMailContent protectedContent =
            ProtectedMailContent.of(new byte[]{1, 2, 3});
        String plaintext =
            "Use https://app.payflow.local/recover-password?token=secret";
        UUID messageId = UUID.fromString(
            "88d44452-e807-45c1-90d1-a54d0e797c9c"
        );
        MailOutboxMessage message = MailOutboxMessage.pending(
            messageId,
            UUID.randomUUID(),
            MailOutboxPurpose.PASSWORD_RECOVERY,
            "nursena@example.com",
            "Reset your PayFlow password",
            protectedContent,
            "<account-action-" + messageId + "@payflow.local>",
            Instant.parse("2026-08-05T18:00:00Z"),
            Instant.parse("2026-08-05T18:30:00Z")
        );
        when(contentProtection.reveal(
            message.protectionContext(),
            protectedContent
        )).thenReturn(plaintext);
        SmtpMailDeliveryAdapter adapter = new SmtpMailDeliveryAdapter(
            mailSender,
            new MailDeliveryProperties(
                "no-reply@payflow.local",
                "PayFlow"
            ),
            contentProtection
        );

        adapter.send(message);

        verify(mailSender).send(mimeMessage);
        assertThat(mimeMessage.getSubject())
            .isEqualTo("Reset your PayFlow password");
        assertThat(mimeMessage.getAllRecipients())
            .extracting(Object::toString)
            .containsExactly("nursena@example.com");
        assertThat(mimeMessage.getHeader("Message-ID"))
            .containsExactly(message.messageId());
        assertThat(mimeMessage.getContent().toString())
            .contains("token=secret");
        assertThat(message.toString()).doesNotContain("token=secret");
    }
}

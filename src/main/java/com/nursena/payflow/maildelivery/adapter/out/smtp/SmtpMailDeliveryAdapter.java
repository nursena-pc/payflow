package com.nursena.payflow.maildelivery.adapter.out.smtp;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Objects;

import com.nursena.payflow.maildelivery.application.port.out.MailContentProtectionPort;
import com.nursena.payflow.maildelivery.application.port.out.MailDeliveryPort;
import com.nursena.payflow.maildelivery.domain.model.MailOutboxMessage;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
class SmtpMailDeliveryAdapter implements MailDeliveryPort {

    private final JavaMailSender mailSender;
    private final MailDeliveryProperties properties;
    private final MailContentProtectionPort contentProtection;

    SmtpMailDeliveryAdapter(
        JavaMailSender mailSender,
        MailDeliveryProperties properties,
        MailContentProtectionPort contentProtection
    ) {
        this.mailSender = Objects.requireNonNull(mailSender, "mailSender must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.contentProtection = Objects.requireNonNull(contentProtection, "contentProtection must not be null");
    }

    @Override
    public void send(MailOutboxMessage message) {
        MailOutboxMessage checkedMessage = Objects.requireNonNull(
            message,
            "message must not be null"
        );
        if (checkedMessage.protectedContent() == null) {
            throw new IllegalArgumentException("protectedContent must be available for delivery");
        }

        String plaintextBody = contentProtection.reveal(
            checkedMessage.protectionContext(),
            checkedMessage.protectedContent()
        );

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                mimeMessage,
                false,
                StandardCharsets.UTF_8.name()
            );
            helper.setFrom(new InternetAddress(
                properties.fromAddress(),
                properties.fromName(),
                StandardCharsets.UTF_8.name()
            ));
            helper.setTo(checkedMessage.recipient());
            helper.setSubject(checkedMessage.subject());
            helper.setText(plaintextBody, false);
            helper.setSentDate(Date.from(checkedMessage.createdAt()));
            mimeMessage.setHeader("Message-ID", checkedMessage.messageId());
            mimeMessage.setHeader("Auto-Submitted", "auto-generated");
            mimeMessage.setHeader("X-Auto-Response-Suppress", "All");
            mailSender.send(mimeMessage);
        } catch (MessagingException | UnsupportedEncodingException | MailException exception) {
            throw new MailDeliveryException(exception);
        }
    }
}

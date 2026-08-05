package com.nursena.payflow.maildelivery.adapter.in.user;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.nursena.payflow.maildelivery.application.port.out.MailContentProtectionPort;
import com.nursena.payflow.maildelivery.application.port.out.MailOutboxEnqueuePort;
import com.nursena.payflow.maildelivery.domain.model.MailContentProtectionContext;
import com.nursena.payflow.maildelivery.domain.model.MailOutboxMessage;
import com.nursena.payflow.maildelivery.domain.model.MailOutboxPurpose;
import com.nursena.payflow.user.application.port.out.AccountActionMail;
import com.nursena.payflow.user.application.port.out.AccountActionMailPort;
import com.nursena.payflow.user.domain.model.AccountActionCredentialPurpose;
import org.springframework.stereotype.Component;

@Component
class AccountActionMailOutboxAdapter
    implements AccountActionMailPort {

    private final MailOutboxEnqueuePort enqueuePort;
    private final AccountActionMailContentFactory contentFactory;
    private final MailContentProtectionPort contentProtection;
    private final Clock clock;

    AccountActionMailOutboxAdapter(
        MailOutboxEnqueuePort enqueuePort,
        AccountActionMailContentFactory contentFactory,
        MailContentProtectionPort contentProtection,
        Clock clock
    ) {
        this.enqueuePort = Objects.requireNonNull(enqueuePort, "enqueuePort must not be null");
        this.contentFactory = Objects.requireNonNull(contentFactory, "contentFactory must not be null");
        this.contentProtection = Objects.requireNonNull(contentProtection, "contentProtection must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void enqueue(AccountActionMail mail) {
        AccountActionMail checkedMail = Objects.requireNonNull(mail, "mail must not be null");
        Instant createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        AccountActionMailContent content = contentFactory.create(checkedMail);
        MailOutboxPurpose purpose = checkedMail.purpose()
            == AccountActionCredentialPurpose.EMAIL_VERIFICATION
                ? MailOutboxPurpose.EMAIL_VERIFICATION
                : MailOutboxPurpose.PASSWORD_RECOVERY;
        String messageId = "<account-action-"
            + checkedMail.credentialId()
            + "@payflow.local>";

        MailContentProtectionContext protectionContext =
            new MailContentProtectionContext(
                checkedMail.credentialId(),
                checkedMail.userId(),
                purpose,
                checkedMail.recipient().value(),
                content.subject()
            );

        MailOutboxMessage message = MailOutboxMessage.pending(
            checkedMail.credentialId(),
            checkedMail.userId(),
            purpose,
            checkedMail.recipient().value(),
            content.subject(),
            contentProtection.protect(
                protectionContext,
                content.sensitiveBody()
            ),
            messageId,
            createdAt,
            checkedMail.expiresAt()
        );

        enqueuePort.replaceUnresolved(message, createdAt);
    }
}

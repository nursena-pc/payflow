package com.nursena.payflow.maildelivery.adapter.in.user;

import java.util.Objects;

import com.nursena.payflow.user.application.port.out.AccountActionMail;
import com.nursena.payflow.user.domain.model.AccountActionCredentialPurpose;
import org.springframework.stereotype.Component;

@Component
class AccountActionMailContentFactory {

    AccountActionMailContent create(AccountActionMail mail) {
        AccountActionMail checkedMail = Objects.requireNonNull(
            mail,
            "mail must not be null"
        );
        if (checkedMail.purpose() == AccountActionCredentialPurpose.EMAIL_VERIFICATION) {
            return new AccountActionMailContent(
                "Verify your PayFlow email",
                "Complete your PayFlow email verification using the secure link below.\n\n"
                    + checkedMail.confirmationLink()
                    + "\n\nThis link expires at "
                    + checkedMail.expiresAt()
                    + ". If you did not create this account, ignore this message."
            );
        }
        return new AccountActionMailContent(
            "Reset your PayFlow password",
            "Complete your PayFlow password recovery using the secure link below.\n\n"
                + checkedMail.confirmationLink()
                + "\n\nThis link expires at "
                + checkedMail.expiresAt()
                + ". If you did not request a password reset, ignore this message."
        );
    }
}

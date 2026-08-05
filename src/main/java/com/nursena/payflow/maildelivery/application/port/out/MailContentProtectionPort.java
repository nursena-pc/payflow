package com.nursena.payflow.maildelivery.application.port.out;

import com.nursena.payflow.maildelivery.domain.model.MailContentProtectionContext;
import com.nursena.payflow.maildelivery.domain.model.ProtectedMailContent;

public interface MailContentProtectionPort {

    ProtectedMailContent protect(
        MailContentProtectionContext context,
        String plaintext
    );

    String reveal(
        MailContentProtectionContext context,
        ProtectedMailContent protectedContent
    );
}

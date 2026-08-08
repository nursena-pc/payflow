package com.nursena.payflow.user.application.port.out;

import com.nursena.payflow.user.domain.model.EmailAddress;

public interface TotpProvisioningUriPort {

    String build(
        EmailAddress account,
        String base32Secret
    );
}

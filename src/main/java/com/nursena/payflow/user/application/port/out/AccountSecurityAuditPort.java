package com.nursena.payflow.user.application.port.out;

import com.nursena.payflow.user.domain.model.AccountSecurityAuditEvent;

public interface AccountSecurityAuditPort {

    void append(AccountSecurityAuditEvent event);
}

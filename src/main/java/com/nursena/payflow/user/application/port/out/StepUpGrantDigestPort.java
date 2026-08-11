package com.nursena.payflow.user.application.port.out;

import com.nursena.payflow.user.domain.model.StepUpGrantDigest;

public interface StepUpGrantDigestPort {
    StepUpGrantDigest digest(String value);
}

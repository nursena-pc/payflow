package com.nursena.payflow.user.application.port.in;

import java.util.UUID;

import com.nursena.payflow.user.domain.model.StepUpPurpose;

public interface StepUpAuthorizationPolicy {

    void requireAndConsume(
        UUID subjectId,
        StepUpPurpose purpose,
        String grantToken
    );
}

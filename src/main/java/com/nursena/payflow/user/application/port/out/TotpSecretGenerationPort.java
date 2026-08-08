package com.nursena.payflow.user.application.port.out;

public interface TotpSecretGenerationPort {

    GeneratedTotpSecret generate();
}

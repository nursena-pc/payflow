package com.nursena.payflow.user.application.port.out;

import java.net.URI;

public interface PasswordRecoveryLinkPort {

    URI build(String credential);
}

package com.nursena.payflow.user.application.port.out;

public interface PasswordHashingPort {

    String hash(String rawPassword);
}

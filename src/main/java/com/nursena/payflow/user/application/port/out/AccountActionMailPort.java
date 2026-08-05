package com.nursena.payflow.user.application.port.out;

public interface AccountActionMailPort {

    void enqueue(AccountActionMail mail);
}

package com.nursena.payflow.maildelivery.application.port.out;

import com.nursena.payflow.maildelivery.domain.model.MailOutboxMessage;

public interface MailDeliveryPort {

    void send(MailOutboxMessage message);
}

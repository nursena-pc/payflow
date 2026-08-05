package com.nursena.payflow.maildelivery.application.port.in;

public interface DispatchMailOutboxUseCase {

    DispatchMailOutboxResult dispatch(
        DispatchMailOutboxCommand command
    );
}

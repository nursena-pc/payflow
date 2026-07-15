package com.nursena.payflow.transaction.application.port.in;

public interface TransferMoneyUseCase {

    TransferMoneyResult transfer(
        TransferMoneyCommand command
    );
}

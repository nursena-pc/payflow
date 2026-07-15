package com.nursena.payflow.transaction.domain.exception;

import com.nursena.payflow.common.exception.BusinessRuleException;

public final class SelfTransferNotAllowedException
    extends BusinessRuleException {

    public SelfTransferNotAllowedException() {
        super(
            "SELF_TRANSFER_NOT_ALLOWED",
            "Source and target wallets must be different."
        );
    }
}

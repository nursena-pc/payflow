package com.nursena.payflow.common.exception;

public abstract class BusinessRuleException extends RuntimeException {

    private final String code;

    protected BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

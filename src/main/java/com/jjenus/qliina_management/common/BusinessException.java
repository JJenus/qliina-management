package com.jjenus.qliina_management.common;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final String errorCode;
    private final String field;

    public BusinessException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.field = null;
    }

    public BusinessException(String message, String errorCode, String field) {
        super(message);
        this.errorCode = errorCode;
        this.field = field;
    }

    public BusinessException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.field = null;
    }
}

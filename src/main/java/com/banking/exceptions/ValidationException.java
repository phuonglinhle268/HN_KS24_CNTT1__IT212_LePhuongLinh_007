package com.banking.exceptions;

public class ValidationException extends BusinessException {
    public ValidationException(String message) {
        super(400, message);
    }
}

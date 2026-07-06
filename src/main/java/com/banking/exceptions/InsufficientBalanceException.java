package com.banking.exceptions;

public class InsufficientBalanceException extends BusinessException {
    public InsufficientBalanceException(String message) {
        super(400, message);
    }
}

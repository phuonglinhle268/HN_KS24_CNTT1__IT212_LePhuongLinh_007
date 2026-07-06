package com.banking.exceptions;

public class AccountNotFoundException extends BusinessException {
    public AccountNotFoundException(String message) {
        super(404, message);
    }
}

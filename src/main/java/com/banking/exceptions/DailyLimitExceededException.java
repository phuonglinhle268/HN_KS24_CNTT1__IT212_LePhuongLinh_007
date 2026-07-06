package com.banking.exceptions;

public class DailyLimitExceededException extends BusinessException {
    public DailyLimitExceededException(String message) {
        super(429, message);
    }
}

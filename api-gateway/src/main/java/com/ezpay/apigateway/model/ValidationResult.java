package com.ezpay.apigateway.model;

import org.springframework.http.HttpStatus;

public class ValidationResult {
    private final boolean success;
    private final HttpStatus status;
    private final String errorCode;
    private final String message;

    private ValidationResult(boolean success, HttpStatus status, String errorCode, String message) {
        this.success = success;
        this.status = status;
        this.errorCode = errorCode;
        this.message = message;
    }

    public static ValidationResult buildSuccess() {
        return new ValidationResult(true, HttpStatus.OK, null, null);
    }

    public static ValidationResult failure(HttpStatus status, String errorCode, String message) {
        return new ValidationResult(false, status, errorCode, message);
    }

    public boolean success() {
        return success;
    }

    public HttpStatus status() {
        return status;
    }

    public String errorCode() {
        return errorCode;
    }

    public String message() {
        return message;
    }
}
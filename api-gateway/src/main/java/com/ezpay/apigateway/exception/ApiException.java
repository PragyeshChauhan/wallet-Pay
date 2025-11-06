package com.ezpay.apigateway.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String validation;
    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
        this.validation = "Not specified";
    }
    public ApiException(HttpStatus status, String message,String validation) {
        super(message);
        this.status = status;
        this.validation = validation;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
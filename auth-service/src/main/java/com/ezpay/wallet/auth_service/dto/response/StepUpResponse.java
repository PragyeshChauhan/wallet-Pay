package com.ezpay.wallet.auth_service.dto.response;


public class StepUpResponse {
    private String stepUpToken;
    private long expiresAt;

    public StepUpResponse(long expiresAt, String stepUpToken) {
        this.expiresAt = expiresAt;
        this.stepUpToken = stepUpToken;
    }
}
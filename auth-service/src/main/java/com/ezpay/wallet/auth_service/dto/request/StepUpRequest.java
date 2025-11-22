package com.ezpay.wallet.auth_service.dto.request;

import jakarta.validation.constraints.NotBlank;


public class StepUpRequest {
    @NotBlank
    private String deviceId;

    @NotBlank
    private String accessToken;

    private String pin;
    private String biometricToken;

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getBiometricToken() {
        return biometricToken;
    }

    public void setBiometricToken(String biometricToken) {
        this.biometricToken = biometricToken;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }
}

package com.ezpay.wallet.auth_service.dto.request;

import jakarta.validation.constraints.NotBlank;


public class DeviceRegistration {


    @NotBlank
    private String deviceId;

    private String deviceModel;
    private String platform;
    private String publicKeyPem;


    public DeviceRegistration(String deviceId, String deviceModel, String mobile, String otp, String platform) {
        this.deviceId = deviceId;
        this.deviceModel = deviceModel;
        this.platform = platform;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceModel() {
        return deviceModel;
    }

    public void setDeviceModel(String deviceModel) {
        this.deviceModel = deviceModel;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    public void setPublicKeyPem(String publicKeyPem) {
        this.publicKeyPem = publicKeyPem;
    }
}

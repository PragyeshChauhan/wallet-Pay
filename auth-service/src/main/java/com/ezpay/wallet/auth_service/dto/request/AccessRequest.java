package com.ezpay.wallet.auth_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public  class AccessRequest {

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "\\d{10}", message = "Invalid mobile number")
    private String mobileNumber;

    @NotBlank(message = "Device ID is required")
    private String deviceId;

    @NotBlank(message = "userName is required")
    private String userName;

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
}

package com.ezpay.apigateway.model;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for authentication requests.
 */
public class AuthRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    private String otp;
    private String pin;
    private String mfaCode;

    // Getters and setters
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }
    public String getPin() { return pin; }
    public void setPin(String pin) { this.pin = pin; }
    public String getMfaCode() { return mfaCode; }
    public void setMfaCode(String mfaCode) { this.mfaCode = mfaCode; }
}
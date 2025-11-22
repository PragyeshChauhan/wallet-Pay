package com.ezpay.userservice.dto;

public class records {

//    @jakarta.validation.constraints.NotBlank(message = "otp is required")
    public record LoginRequest(
            String username,
            String password,
            String deviceId,
            @jakarta.validation.constraints.NotBlank(message = "otp is required")String otp,
            @jakarta.validation.constraints.NotBlank(message = "mobile is required")String mobile,
            String pin
    ) {}

    public record SignupRequest(String mobile, String pin, String username, String email, String password,
                                boolean enableBiometric, String walletPin, boolean enableNotification) {}

    public record PasswordResetRequest(String mobile) {}

    public record PasswordResetConfirm(String mobile, String otp, String newPin) {}

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            long expiresIn
    ) {}


}

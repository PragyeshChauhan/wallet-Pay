package com.ezpay.notificationservice.service;

import com.ezpay.notificationservice.config.TwilioConfig;
import com.twilio.exception.ApiException;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;


@Service
public class SmsService {

    private static final Logger logger = LoggerFactory.getLogger(SmsService.class);

    @Value("${twilio.verify-service-sid}")
    private String verifyServiceSid;

    private final TwilioConfig config;

    public SmsService(TwilioConfig config) {
        this.config = config;
    }

    /**
     * Sends OTP verification to the specified phone number
     * @param phoneNumber The phone number to send OTP to (E.164 format)
     * @return Verification object
     * @throws IllegalArgumentException if phoneNumber is invalid
     * @throws ApiException if Twilio API call fails
     */
    public Verification sendVerification(@NotBlank String phoneNumber) {
        validatePhoneNumber(phoneNumber);

        try {
            logger.info("Initiating OTP verification for phone number: {}", phoneNumber);
            Verification verification = Verification.creator(
                    verifyServiceSid,
                    phoneNumber,
                    "sms"
            ).create();
            logger.info("OTP sent successfully to: {}", phoneNumber);
            return verification;
        } catch (ApiException e) {
            logger.error("Failed to send OTP to {}: {}", phoneNumber, e.getMessage());
            throw e;
        }
    }

    /**
     * Verifies the OTP code for the given phone number
     * @param phoneNumber The phone number to verify (E.164 format)
     * @param code The OTP code to verify
     * @return VerificationCheck object
     * @throws IllegalArgumentException if phoneNumber or code is invalid
     * @throws ApiException if Twilio API call fails
     */
    public VerificationCheck checkVerification(@NotBlank String phoneNumber, @NotBlank String code) {
        validatePhoneNumber(phoneNumber);
        validateCode(code);

        try {
            logger.info("Verifying OTP for phone number: {}", phoneNumber);
            VerificationCheck verificationCheck = VerificationCheck.creator(verifyServiceSid)
                    .setCode(code)
                    .setTo(phoneNumber)
                    .create();
            logger.info("OTP verification result for {}: {}", phoneNumber, verificationCheck.getStatus());
            return verificationCheck;
        } catch (ApiException e) {
            logger.error("OTP verification failed for {}: {}", phoneNumber, e.getMessage());
            throw e;
        }
    }

    /**
     * Validates phone number format
     * @param phoneNumber The phone number to validate
     * @throws IllegalArgumentException if phoneNumber is invalid
     */
    private void validatePhoneNumber(String phoneNumber) {
        if (!StringUtils.hasText(phoneNumber) || !phoneNumber.matches("\\+\\d{10,15}")) {
            logger.error("Invalid phone number format: {}", phoneNumber);
            throw new IllegalArgumentException("Phone number must be in E.164 format (e.g., +1234567890)");
        }
    }

    /**
     * Validates OTP code
     * @param code The OTP code to validate
     * @throws IllegalArgumentException if code is invalid
     */
    private void validateCode(String code) {
        if (!StringUtils.hasText(code) || !code.matches("\\d{4,6}")) {
            logger.error("Invalid OTP code format");
            throw new IllegalArgumentException("OTP code must be 4-6 digits");
        }
    }
}
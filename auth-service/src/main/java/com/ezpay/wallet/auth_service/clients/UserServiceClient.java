package com.ezpay.wallet.auth_service.clients;

import com.ezpay.infraservice.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class UserServiceClient {
    private static final Logger logger = LoggerFactory.getLogger(UserServiceClient.class);
    @Value("${url.service.user}")
    private String userServiceUrl;
    private final RestTemplate restTemplate = new RestTemplate();

    public Long getUserIdByMobile(String mobile) {
        try {
            String response = restTemplate.getForObject(userServiceUrl + "/users/" + mobile, String.class);
            logger.debug("Fetched user ID for mobile: {}", mobile);
            return response != null ? Long.parseLong(response) : null;
        } catch (Exception e) {
            logger.error("Failed to fetch user ID for mobile: {}", mobile, e);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Failed to fetch user from UserService");
        }
    }

    public boolean validatePin(String userId, String pin) {
        try {
            Boolean valid = restTemplate.postForObject(userServiceUrl + "/users/validate-pin",
                    new PinValidationRequest(userId, pin), Boolean.class);
            logger.debug("PIN validation result for user: {}: {}", userId, valid);
            return valid != null ? valid : false;
        } catch (Exception e) {
            logger.error("Failed to validate PIN for user: {}", userId, e);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Failed to validate PIN with UserService");
        }
    }

    public String getMobileUpdatedAt(String userId) {
        try {
            String updatedAt = restTemplate.getForObject(userServiceUrl + "/users/" + userId + "/mobile-updated-at", String.class);
            logger.debug("Fetched mobile updated timestamp for user: {}", userId);
            return updatedAt;
        } catch (Exception e) {
            logger.error("Failed to fetch mobile updated timestamp for user: {}", userId, e);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Failed to fetch mobile update timestamp");
        }
    }
}

class PinValidationRequest {
    private String userId;
    private String pin;

    public PinValidationRequest(String userId, String pin) {
        this.userId = userId;
        this.pin = pin;
    }

    public String getUserId() {
        return userId;
    }

    public String getPin() {
        return pin;
    }
}

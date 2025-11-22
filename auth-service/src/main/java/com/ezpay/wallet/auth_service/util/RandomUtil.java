package com.ezpay.wallet.auth_service.util;

import com.ezpay.infraservice.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.security.SecureRandom;

public class RandomUtil {
    private static final SecureRandom random = new SecureRandom();
    private static final String ALPHA_NUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    public static String secureRandomToken(int length, String chars) {
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < length; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate random token");
        }
    }

    public static String secureRandomToken(int length) {
        return secureRandomToken(length, ALPHA_NUM);
    }
}
package com.ezpay.wallet.auth_service.service;

import com.ezpay.infraservice.exception.ApiException;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
public class RateLimiterService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimiterService.class);
    private static final int DEFAULT_LIMIT = 5; // Aligns with Paytm/PhonePe typical limits
    private static final long DEFAULT_WINDOW_MINUTES = 1; // 1-minute sliding window

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @Value("${rate.limiter.limit.per.minute:5}")
    private int rateLimitPerMinute;

    @Value("${rate.limiter.window.minutes:1}")
    private long rateLimitWindowMinutes;

    public boolean allowRequest(String key, String userType) {
        String redisKey = "rate:limit:" + userType + ":" + key + ":" + Instant.now().toEpochMilli() / (rateLimitWindowMinutes * 60 * 1000);
        Long count = redisTemplate.opsForValue().increment(redisKey);

        if (count == 1) {
            redisTemplate.expire(redisKey, rateLimitWindowMinutes, TimeUnit.MINUTES);
        }

        meterRegistry.counter("auth.rate_limit.requests", "key", key, "userType", userType, "window", String.valueOf(rateLimitWindowMinutes)).increment();
        int effectiveLimit = getEffectiveLimit(userType);

        if (count > effectiveLimit) {
            long retryAfterSeconds = rateLimitWindowMinutes * 60 - (Instant.now().toEpochMilli() % (rateLimitWindowMinutes * 60 * 1000)) / 1000;
            logger.warn("Rate limit exceeded for key: {}, userType: {}, count: {}, retryAfter: {}s", key, userType, count, retryAfterSeconds);
            meterRegistry.counter("auth.rate_limit.exceeded", "key", key, "userType", userType).increment();
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded. Please try again after " + retryAfterSeconds + " seconds.");
        }

        logger.debug("Allowed request for key: {}, userType: {}, count: {}", key, userType, count);
        return true;
    }

    private int getEffectiveLimit(String userType) {
        // Dynamic limits based on user type (e.g., new users vs. registered)
        return switch (userType.toLowerCase()) {
            case "existing" -> rateLimitPerMinute / 2; // Stricter limit for new users
            case "registered" -> rateLimitPerMinute; // Standard limit for registered users
            default -> DEFAULT_LIMIT; // Default limit
        };
    }
}
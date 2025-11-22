package com.ezpay.wallet.auth_service.service;

import com.ezpay.infraservice.exception.ApiException;
import com.ezpay.wallet.auth_service.clients.UserServiceClient;
import com.ezpay.wallet.auth_service.entity.Device;
import com.ezpay.wallet.auth_service.repository.DeviceRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
public class RiskService {

    private static final Logger logger = LoggerFactory.getLogger(RiskService.class);
    private static final long IP_VELOCITY_WINDOW_MINUTES = 5;
    private static final long SIM_SWAP_WINDOW_DAYS = 1;
    private static final int LOGIN_FREQUENCY_THRESHOLD = 10;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private UserServiceClient userServiceClient;

    @Autowired
    private HttpServletRequest httpServletRequest;

    @Autowired
    private MeterRegistry meterRegistry;

    @Value("${risk.ip.velocity.threshold:300000}") // 5 minutes in milliseconds
    private long ipVelocityThreshold;

    @Value("${risk.login.frequency.threshold:10}")
    private int loginFrequencyThreshold;

    public void evaluateRefresh(String userId, String deviceId) {
        logger.info("Evaluating refresh risk for user: {}, device: {}", userId, deviceId);

        // IP Velocity Check
        String ipKey = "risk:ip:" + userId;
        String currentIp = httpServletRequest.getRemoteAddr();
        String lastIp = redisTemplate.opsForValue().get(ipKey);
        Long lastIpTime = lastIp != null ? Long.parseLong(Objects.requireNonNull(redisTemplate.opsForValue().get(ipKey + ":time"))) : 0L;

        if (lastIp != null && !lastIp.equals(currentIp) && System.currentTimeMillis() - lastIpTime < ipVelocityThreshold) {
            logger.warn("IP velocity violation for user: {}, device: {}, currentIp: {}, lastIp: {}, timeDiff: {}ms",
                    userId, deviceId, currentIp, lastIp, System.currentTimeMillis() - lastIpTime);
            meterRegistry.counter("risk.ip_velocity_violations", "userId", userId, "deviceId", deviceId).increment();
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Suspicious IP change detected. Please verify your identity.");
        }
        redisTemplate.opsForValue().set(ipKey, currentIp, Duration.ofHours(1).toMinutes(), TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(ipKey + ":time", String.valueOf(System.currentTimeMillis()), Duration.ofHours(1).toMinutes(), TimeUnit.MINUTES);

        // SIM Swap Check
        String mobileUpdatedAt = userServiceClient.getMobileUpdatedAt(userId);
        if (mobileUpdatedAt != null && Instant.parse(mobileUpdatedAt).isAfter(Instant.now().minus(Duration.ofDays(SIM_SWAP_WINDOW_DAYS)))) {
            logger.warn("Recent SIM swap detected for user: {}, updatedAt: {}", userId, mobileUpdatedAt);
            meterRegistry.counter("risk.sim_swap_detections", "userId", userId).increment();
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Recent mobile number change detected. Please contact support.");
        }

        // Device Consistency Check
        Device device = deviceRepository.findByDeviceIdAndUserId(deviceId, Long.parseLong(userId))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Device not registered"));

        // Login Frequency Check
        String loginKey = "risk:login:" + userId;
        Long loginCount = redisTemplate.opsForValue().increment(loginKey);
        if (loginCount !=null && loginCount == 1) {
            redisTemplate.expire(loginKey, Duration.ofHours(1).toMinutes(), TimeUnit.MINUTES);
        }
        if (loginCount ==null  || loginCount > loginFrequencyThreshold) {
            logger.warn("Excessive login attempts for user: {}, count: {}", userId, loginCount);
            meterRegistry.counter("risk.login_frequency_exceeded", "userId", userId).increment();
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts. Please try again later or contact support.");
        }

        // Device Fingerprint Check (simplified)
        if (!device.getFingerPrint().equals(getDeviceFingerprint(httpServletRequest))) {
            logger.warn("Device fingerprint mismatch for user: {}, device: {}", userId, deviceId);
            meterRegistry.counter("risk.device_mismatch", "userId", userId, "deviceId", deviceId).increment();
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Device mismatch detected. Please verify your device.");
        }

        logger.debug("Risk evaluation passed for user: {}, device: {}", userId, deviceId);
        meterRegistry.counter("risk.evaluation.success", "userId", userId, "deviceId", deviceId).increment();
    }

    public boolean requiresStepUp(String userId, String deviceId) {
        Device device = deviceRepository.findByDeviceIdAndUserId(deviceId, Long.parseLong(userId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Device not found"));
        boolean requiresStepUp = device.getCreatedAt().isAfter(Instant.now().minus(Duration.ofDays(1))) || !device.isVerified() ;
        logger.info("Step-up required for device: {}: {}", deviceId, requiresStepUp);
        meterRegistry.counter("risk.step_up_required", "userId", userId, "deviceId", deviceId, "required", String.valueOf(requiresStepUp)).increment();
        return requiresStepUp;
    }

    private String getDeviceFingerprint(HttpServletRequest request) {
        // Simplified fingerprint (in production, use a library like DeviceAtlas or WURFL)
        String userAgent = request.getHeader("User-Agent");
        String ip = request.getRemoteAddr();
        return userAgent + ":" + ip; // Basic hashable string
    }
}
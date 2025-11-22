package com.ezpay.wallet.auth_service.service;

import com.ezpay.infraservice.exception.ApiException;
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
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
public class DeviceService {

    private static final Logger logger = LoggerFactory.getLogger(DeviceService.class);
    private static final long TTL_DAYS = 365;

    private final RedisTemplate<String, Object> redisTemplate;
    private final DeviceRepository deviceRepository;
    private final MeterRegistry meterRegistry;

    @Value("${device.key.rotation.days:30}")
    private int keyRotationDays;

    @Autowired
    public DeviceService(RedisTemplate<String, Object> redisTemplate,
                         DeviceRepository deviceRepository,
                         MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.deviceRepository = deviceRepository;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Register or update a device with a public key received from mobile.
     * Mobile must generate the key pair and send the PEM-encoded public key.
     */
    public Device registerOrUpdate(String deviceId, String model, String platform, String publicKeyPem,
                                   HttpServletRequest httpServletRequest) {

        logger.info("Registering/updating device: {}", deviceId);

        // Validate input
        if (deviceId == null || deviceId.isBlank() || model == null || platform == null) {
            logger.warn("Invalid device registration data: {}", deviceId);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid device registration data");
        }

        if (publicKeyPem == null || publicKeyPem.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Public key is required");
        }

        // Load or create new device entity
        Device device = deviceRepository.findByDeviceId(deviceId).orElseGet(Device::new);

        device.setDeviceId(deviceId);
        device.setModel(model);
        device.setPlatform(platform);
        device.setVerified(true);

        // Device fingerprint check (basic)
        String fingerprint = generateFingerprint(httpServletRequest);
        if (device.getFingerPrint() != null && !device.getFingerPrint().equals(fingerprint)) {
            logger.warn("Fingerprint mismatch for device: {}", deviceId);
            meterRegistry.counter("device.fingerprint_mismatch", "deviceId", deviceId).increment();
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Device fingerprint mismatch");
        }
        device.setFingerPrint(fingerprint);

        // Save PEM-encoded public key
        device.setPublicKeyPem(publicKeyPem.trim());

        // Persist device
        Device savedDevice = deviceRepository.save(device);

        // Cache public key for quick lookup
        cacheDevicePublicKey(savedDevice);

        logger.info("Device [{}] registered/updated successfully", deviceId);
        meterRegistry.counter("device.registration.success", "deviceId", deviceId).increment();
        redisTemplate.opsForValue().set("trust:"+deviceId, "true");
        return savedDevice;
    }

    private String generateFingerprint(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent") != null ? request.getHeader("User-Agent") : "unknown";
        String ipAddress = request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
        return java.util.Base64.getEncoder().encodeToString((userAgent + ":" + ipAddress).getBytes());
    }

    private void cacheDevicePublicKey(Device device) {
        String key = getRedisKey(device.getDeviceId());
        redisTemplate.opsForValue().set(key, device.getPublicKeyPem(), TTL_DAYS, TimeUnit.DAYS);
    }

    public String getPublicKeyFromCache(String deviceId) {
        String key = getRedisKey(deviceId);
        Object cached = redisTemplate.opsForValue().get(key);
        return cached != null ? cached.toString() : null;
    }

    private String getRedisKey(String deviceId) {
        return "device:" + deviceId;
    }
}

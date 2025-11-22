package com.ezpay.wallet.auth_service.service;

import com.ezpay.infraservice.exception.ApiException;
import com.ezpay.wallet.auth_service.clients.UserServiceClient;
import com.ezpay.wallet.auth_service.dto.request.AccessRequest;
import com.ezpay.wallet.auth_service.dto.request.StepUpRequest;
import com.ezpay.wallet.auth_service.dto.response.StepUpResponse;
import com.ezpay.wallet.auth_service.dto.response.TokenResponse;
import com.ezpay.wallet.auth_service.entity.Device;
import com.ezpay.wallet.auth_service.entity.RefreshToken;
import com.ezpay.wallet.auth_service.repository.DeviceRepository;
import com.ezpay.wallet.auth_service.repository.RefreshTokenRepository;
import com.ezpay.wallet.auth_service.util.TokenHashUtil;
import com.ezpay.wallet.auth_service.util.RandomUtil;
import com.nimbusds.jose.JOSEException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AuthServices {

    private static final Logger logger = LoggerFactory.getLogger(AuthServices.class);

    @Autowired private UserServiceClient userServiceClient;
    @Autowired private DeviceService deviceService;
    @Autowired private JwtService jwtService;
    @Autowired private RiskService riskService;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private DeviceRepository deviceRepository;
    @Autowired private RateLimiterService rateLimiterService;

    @Value("${jwt.refresh-exp}")
    private long rtExp;

    @Value("${jwt.access-exp}")
    private long accessExp;

    /**
     * Checks if the rate limit is exceeded for the given key.
     */
    private void checkRateLimit(String key ,String userType) {
        if (!rateLimiterService.allowRequest(key ,userType)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");
        }
    }

    /**
     * Creates and saves a new refresh token for the given user and device.
     */
    private void createRefreshToken(Long userId, String deviceId, String mobileNumber, String refreshPlain, HttpServletRequest req) {
        String refreshHash = TokenHashUtil.sha256(refreshPlain);
        RefreshToken rt = new RefreshToken();
        rt.setUserId(userId);
        rt.setDeviceId(deviceId);
        rt.setMobileNumber(mobileNumber);
        rt.setTokenHash(refreshHash);
        rt.setExpiresAt(Instant.now().plusSeconds(rtExp));
        rt.setRevoked(false);
        rt.setIp(req.getRemoteAddr());
        rt.setUserAgent(req.getHeader("User-Agent"));
        refreshTokenRepository.save(rt);
    }

    /**
     * Issues a token response with access and refresh tokens.
     */
    private TokenResponse issueTokenResponse(String mobileNumber, String userId, String deviceId, String refreshPlain, HttpServletRequest req) {
        String accessToken = null;
        try {
            accessToken = jwtService.issueAccess(mobileNumber, userId, deviceId);
        } catch (JOSEException e) {
           return null;
        }
        createRefreshToken(Long.valueOf(userId), deviceId, mobileNumber, refreshPlain, req);
        return new TokenResponse(accessToken, refreshPlain, accessExp, rtExp);
    }

    @Transactional
    public TokenResponse rotateRefresh(String refreshPlain, String deviceId, HttpServletRequest req) {
        logger.info("Rotating refresh token for device: {}", deviceId);
        checkRateLimit(deviceId ,"existing");

        String hash = TokenHashUtil.sha256(refreshPlain);
        RefreshToken old = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (old.isRevoked() || old.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Token Expired or revoked refresh");
        }

        if (!old.getDeviceId().equals(deviceId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Token/device mismatch");
        }

//        riskService.evaluateRefresh(old.getUserId().toString(), deviceId);

        if (old.isRevoked()) {
            revokeAllForDevice(deviceId);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token reuse detected");
        }

        old.setRevoked(true);
        old.setRotatedAt(Instant.now());
        refreshTokenRepository.save(old);

        String newRefreshPlain = RandomUtil.secureRandomToken(64);
        return issueTokenResponse(old.getMobileNumber(), old.getUserId().toString(), deviceId, newRefreshPlain, req);
    }

    @Transactional
    public void revokeAllForDevice(String deviceId) {
        logger.warn("Revoking all tokens for device: {}", deviceId);
        Device device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Device not found"));
        List<RefreshToken> tokens = refreshTokenRepository.findByDeviceId(deviceId);
        tokens.forEach(token -> {
            token.setRevoked(true);
            token.setRotatedAt(Instant.now());
        });
        refreshTokenRepository.saveAll(tokens);
        logger.info("All tokens revoked for device: {}", deviceId);
    }

    public TokenResponse accessBundle(AccessRequest accessRequest, HttpServletRequest req) {
        logger.info("Processing Access Bundle for mobile: {}, device: {}", accessRequest.getMobileNumber(), accessRequest.getDeviceId());
        if (accessRequest.getMobileNumber() == null || accessRequest.getMobileNumber().isBlank()) {
            throw new IllegalArgumentException("Mobile number is required");
        }
        if (accessRequest.getDeviceId() == null || accessRequest.getDeviceId().isBlank()) {
            throw new IllegalArgumentException("Device ID is required");
        }

        String refreshPlain = RandomUtil.secureRandomToken(64);
        return issueTokenResponse(accessRequest.getMobileNumber(), accessRequest.getUserName(), accessRequest.getDeviceId(), refreshPlain, req);
    }

    public StepUpResponse validateStepUp(StepUpRequest stepUpRequest) {
        logger.info("Validating step-up for device: {}", stepUpRequest.getDeviceId());
        String userId = jwtService.getSubject(stepUpRequest.getAccessToken());
        String mobileNumber = jwtService.getMobileNumber(stepUpRequest.getAccessToken());
        if (riskService.requiresStepUp(userId, stepUpRequest.getDeviceId())) {
            if (stepUpRequest.getPin() != null) {
                if (!userServiceClient.validatePin(userId, stepUpRequest.getPin())) {
                    throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid PIN");
                }
            } else if (stepUpRequest.getBiometricToken() != null) {
                throw new ApiException(HttpStatus.NOT_IMPLEMENTED, "Biometric validation not implemented");
            } else {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PIN or biometric token required");
            }
        }
        long exp = System.currentTimeMillis() / 1000 + 300;
        String stepUpToken = jwtService.issueAccessByExp(mobileNumber, userId, stepUpRequest.getDeviceId(), exp);
        logger.info("Issued step-up token for user: {}, device: {}", userId, stepUpRequest.getDeviceId());
        return new StepUpResponse(exp, stepUpToken);
    }
}
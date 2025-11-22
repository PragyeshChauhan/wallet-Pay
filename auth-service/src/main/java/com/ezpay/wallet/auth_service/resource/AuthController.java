package com.ezpay.wallet.auth_service.resource;

import com.ezpay.wallet.auth_service.dto.request.*;
import com.ezpay.wallet.auth_service.dto.response.StepUpResponse;
import com.ezpay.wallet.auth_service.dto.response.TokenResponse;
import com.ezpay.wallet.auth_service.entity.Device;
import com.ezpay.wallet.auth_service.service.AuthServices;
import com.ezpay.wallet.auth_service.service.DeviceService;
import com.ezpay.wallet.auth_service.util.AuthConstant;
import com.ezpay.wallet.auth_service.util.ResponseUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    @Autowired private AuthServices authService;
    @Autowired private DeviceService deviceService;

    @PostMapping("/access")
    public ResponseEntity<TokenResponse> onboard(@Valid @RequestBody AccessRequest req,
                                                 HttpServletRequest httpReq) {
        logger.info("Onboarding request for device: {}, userId: {}", req.getDeviceId(), req.getUserName());
        TokenResponse resp = authService.accessBundle(req, httpReq);
        logger.info("Onboarding successful for device: {}", req.getDeviceId());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestHeader("RefreshToken") String refreshToken,
                                                 @RequestHeader("DeviceId") String deviceId,
                                                 HttpServletRequest httpReq) {
        try {
            logger.info("Refreshing token for device: {}", deviceId);
            TokenResponse resp = authService.rotateRefresh(refreshToken, deviceId, httpReq);
            logger.info("Refreshed tokens for device: {}", deviceId);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("Message","API execution fail as "+e.getMessage()));
        }
    }


    @PostMapping("/register-device")
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody DeviceRegistration req,
                                                   HttpServletRequest httpReq , HttpServletResponse response ) {
        try {
            logger.info("register device for Id: {}",  req.getDeviceId());
            Device device = deviceService.registerOrUpdate(req.getDeviceId(), req.getDeviceModel(), req.getPlatform() ,req.getPublicKeyPem(), httpReq);

            logger.info("Registration Successfully: {}...", req.getDeviceId());
            return ResponseEntity.ok(device);
        } catch (Exception e) {
            return ResponseUtil.returnResponseEntity(response ,400 , AuthConstant.OTP_VERIFICATION_FAIL ,e.getMessage());
        }
    }

    @PostMapping("/suspend/device")
    public ResponseEntity<Void> revokeDevice(@RequestHeader("Device-Id") String deviceId) {
        logger.warn("Revoking all tokens for device: {}", deviceId);
        authService.revokeAllForDevice(deviceId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/step-up")
    public ResponseEntity<StepUpResponse> stepUp(@Valid @RequestBody StepUpRequest req) {
        logger.info("Processing step-up for device: {}", req.getDeviceId());
        StepUpResponse resp = authService.validateStepUp(req);
        logger.info("Issued step-up token for device: {}", req.getDeviceId());
        return ResponseEntity.ok(resp);
    }

}



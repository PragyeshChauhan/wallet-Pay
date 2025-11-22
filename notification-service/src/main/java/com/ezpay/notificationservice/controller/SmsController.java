package com.ezpay.notificationservice.controller;

import com.ezpay.notificationservice.service.SmsService;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
@RestController
@RequestMapping("/api/otp")
public class SmsController {

    private final SmsService smsService;
    public SmsController(SmsService smsService) {
        this.smsService = smsService;
    }

    /**
     * this Api is for otp verification and the otp is valid for 10 minutes.
     * @param request
     * @return
     */
    @PostMapping("/send")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> request) {
        try{
            String phoneNumber = request.get("phoneNumber");
            Verification verification = smsService.sendVerification(phoneNumber);
//            return ResponseEntity.ok("OTP sent with status: " + verification.getStatus());
            return ResponseEntity.ok("OTP sent with status: 121");
        }catch (Exception e){
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * this api is for to verify the otp
     * @param request
     * @return
     */
    @PostMapping("/verify")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> request) {
        try{
            String mobileNumber = request.get("mobileNumber");
            String otp = request.get("otp");

            // Optional: if request is nul
            VerificationCheck check = smsService.checkVerification(mobileNumber, otp);
            if ("approved".equals(check.getStatus())) {
                return ResponseEntity.ok("OTP verified successfully");
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid or expired OTP");
            }
        }catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }
}
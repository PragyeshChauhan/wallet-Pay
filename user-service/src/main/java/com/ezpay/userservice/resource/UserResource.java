package com.ezpay.userservice.resource;

import com.ezpay.userservice.constants.UserServiceConstants;
import com.ezpay.userservice.domain.User;
import com.ezpay.userservice.dto.UserDTO;
import com.ezpay.userservice.dto.records;
import com.ezpay.userservice.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserResource {

    private static final Logger log = LoggerFactory.getLogger(UserResource.class);
    @Autowired
    private UserService userService;

    @Autowired
    private RestTemplate restTemplate;

    @PostMapping("/register")
    public ResponseEntity<?> userRegister(@RequestBody UserDTO userDTO) {
        log.info("Inside userRegister()");
        try {
            return new ResponseEntity<>(userService.register(userDTO), HttpStatus.OK);
        }catch (IllegalArgumentException e){
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }catch (Exception e){
            log.info("Error while calling userRegister()");
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody records.LoginRequest request) {
        log.info("Inside login()");
        try {
            String otpUrl = "http://notification-service/api/otp/verify";
            Map<String, String> otpRequest = Map.of(
                    "mobileNumber", request.mobile(),
                    "otp", request.otp()
            );

            ResponseEntity<String> otpResponse = restTemplate.postForEntity(otpUrl, otpRequest, String.class);

            if (!otpResponse.getStatusCode().is2xxSuccessful()) {
                log.error("[user-service] OTP verification failed. Status: {}", otpResponse.getStatusCode());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("OTP Verification Failed: " + otpResponse.getBody());
            }

            log.info("[user-service] OTP verified. Proceeding to login...");
            // Step 2: check User Exist : if Not Exist Then Create Temp User

            User user = userService.login(request);
            if(user==null){
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found ");
            }

            // Step 3: Login if OTP succeeds
            String loginUrl = "http://auth-service/api/auth/access";
            Map<String, String> loginRequest = Map.of(
                    "mobileNumber", request.mobile(),
                    "deviceId", request.deviceId(),
                    "userName", user.getUserName()
            );

            ResponseEntity<String> loginResponse = restTemplate.postForEntity(loginUrl, loginRequest, String.class);

            if (!loginResponse.getStatusCode().is2xxSuccessful()) {
                log.error("[user-service] Login failed. Status: {}", loginResponse.getStatusCode());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Login Failed: " + loginResponse.getBody());
            }

            return ResponseEntity.ok(loginResponse.getBody());

        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            log.error("[user-service] HTTP error: {}", ex.getResponseBodyAsString());
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.error("[user-service] General error: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Error: " + ex.getMessage());
        }
    }

    @PutMapping
    public ResponseEntity<?> userInfoUpdate(@RequestBody UserDTO userDTO) {
        log.info("Inside userInfoUpdate()");
        try {
            return new ResponseEntity<>(userService.updateUser(userDTO), HttpStatus.OK);
        }
        catch (Exception e){
            log.info("Error while calling userInfoUpdate()");
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetpassword(@RequestBody Map<String,String> resetPasswordObject){
        log.info("Inside userInfoUpdate()");
        try{
            return new ResponseEntity<>(userService.resetUserPassword(resetPasswordObject), HttpStatus.OK);
        }catch (IllegalArgumentException e){
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }catch (Exception e){
            log.error("Error while calling userInfoUpdate()");
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/last-activity")
    public ResponseEntity<?> saveLastActivity(@RequestBody Map<String,String> resetPasswordObject){
        log.info("Inside saveLastActivity()");
        try{
            return new ResponseEntity<>(HttpStatus.OK);
        }catch (IllegalArgumentException e){
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }catch (Exception e){
            log.error("Error while calling saveLastActivity()");
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/test-token")
    public ResponseEntity<?> test(){
        log.info("Inside saveLastActivity()");
        try{
            return ResponseEntity.ok(Map.of("message", "working well"));
        }catch (IllegalArgumentException e){
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }catch (Exception e){
            log.error("Error while calling saveLastActivity()");
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }
}
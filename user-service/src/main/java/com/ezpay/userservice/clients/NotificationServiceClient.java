package com.ezpay.userservice.clients;

import org.springframework.stereotype.Service;

@Service
public class NotificationServiceClient {
//    private static final Logger logger = LoggerFactory.getLogger(NotificationServiceClient.class);
//
//    @Value("${url.service.notification}")
//    private String notificationServiceUrl;
//
//    @Autowired
//    private RestTemplate restTemplate;
//
//
//    public boolean verifyOtp(String mobile, String otp) {
//        try {
//            Boolean valid = restTemplate.postForObject(notificationServiceUrl + "/notifications/verify-otp",
//                    new DeviceRegistration(mobile, otp), Boolean.class);
//            logger.debug("OTP verification result for mobile: {}: {}", mobile, valid);
//            return valid != null ? valid : false;
//        } catch (Exception e) {
//            logger.error("Failed to verify OTP for mobile: {}", mobile, e);
//            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Failed to verify OTP via NotificationService");
//        }
//    }
}


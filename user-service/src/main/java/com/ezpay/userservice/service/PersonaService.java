package com.ezpay.userservice.service;

import com.ezpay.userservice.config.PersonaConfig;
import com.ezpay.userservice.domain.User;
import com.ezpay.userservice.dto.UserEvent;
import com.ezpay.userservice.repository.UserRepository;
import com.ezpay.userservice.serviceImpl.EmailEventProducer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PersonaService {

    private final RestTemplate restTemplate;
    private final PersonaConfig personaConfig;
    private final UserRepository userRepository;
    private final EmailEventProducer emailEventProducer;

    public PersonaService(RestTemplate restTemplate, PersonaConfig personaConfig, UserRepository userRepository,EmailEventProducer emailEventProducer) {
        this.restTemplate = restTemplate;
        this.personaConfig = personaConfig;
        this.userRepository = userRepository;
        this.emailEventProducer = emailEventProducer;
    }

    // Start verification
    public Map<String, Object> startVerification(String userName) {
        String inquiryId = null;
        User user = findUser(userName);
        // Step 1: Create inquiry
        if(user.getPersonaInquiryId() !=null && !user.getPersonaInquiryId().isEmpty()){
            inquiryId = user.getPersonaInquiryId();
        }else{
            inquiryId = createInquiry(user);
        }
        // Step 2: Save inquiry in DB
        saveInquiryToUser(user, inquiryId);

        // Step 3: Create inquiry session (redirect URL)
        String redirectUrl = createInquirySession(inquiryId);
        sendVerificationEmail(user,redirectUrl);
        // Step 4: Return result
        return Map.of(
                "inquiryId", inquiryId,
                "redirectUrl", redirectUrl
        );
    }

    /**
     * method to find user by username
     * @param userName
     * @return
     */
    private User findUser(String userName) {
        return userRepository.findByUserName(userName)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    /**
     * method to create inquiry
     * @param user
     * @return
     */
    private String createInquiry(User user) {
        String inquiryUrl = personaConfig.getBaseUrl() + "/inquiries";
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(buildInquiryBody(user), buildHeaders());
        ResponseEntity<Map> response = new RestTemplate().exchange(inquiryUrl, HttpMethod.POST, entity, Map.class);
        Map<String, Object> responseData = (Map<String, Object>) response.getBody().get("data");
        return (String) responseData.get("id");
    }

    /**
     * save enquiry id in user with status pending
     * @param user
     * @param inquiryId
     */
    private void saveInquiryToUser(User user, String inquiryId) {
        user.setPersonaInquiryId(inquiryId);
        user.setVerificationStatus("PENDING");
        userRepository.save(user);
    }

    /**
     * here we will create the url to complete the kyc , which will be sent to user
     * @param inquiryId
     * @return
     */
    private String createInquirySession(String inquiryId) {
//        String sessionUrl ="https://api.withpersona.com/api/v1/inquiry-sessions";
        String sessionUrl = personaConfig.getBaseUrl() + "/inquiries/" + inquiryId + "/generate-one-time-link";
        HttpEntity<Map<String, Object>> sessionEntity = new HttpEntity<>(buildInquirySessionBody(inquiryId), buildHeaders());
        ResponseEntity<Map> sessionResponse = restTemplate.exchange(sessionUrl, HttpMethod.POST, sessionEntity, Map.class);
        Map<String, Object> responseBody = sessionResponse.getBody();
        if (responseBody == null) {
            throw new RuntimeException("Persona inquiry session response is empty");
        }

        Map<String, Object> meta = (Map<String, Object>) responseBody.get("meta");
        if (meta == null || !meta.containsKey("one-time-link")) {
            throw new RuntimeException("One-time link not found in Persona inquiry session response");
        }

        String oneTimeLink = (String) meta.get("one-time-link");
        return oneTimeLink;



    }


    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(personaConfig.getApiKey());
        return headers;
    }

    private Map<String, Object> buildInquiryBody(User user) {
        return Map.of(
                "data", Map.of(
                        "type", "inquiry",
                        "attributes", Map.of(
                                "inquiry-template-id", personaConfig.getTemplateId(),
                                "reference-id", user.getUserName(),
                                "name-first", user.getFirstName(),
                                "name-last", user.getLastName()
                        )
                )
        );
    }

    private Map<String, Object> buildInquirySessionBody(String inquiryId) {
        return Map.of(
                "data", Map.of(
                        "type", "inquiry-session",
                        "attributes", Map.of(
                                "inquiry-id", inquiryId

                        )
                )
        );
    }

    /**
     * method to send kyc link email
     * @param user
     * @param redirectUrl
     */
    private void sendVerificationEmail(User user, String redirectUrl){
        UserEvent userEvent = new UserEvent();
        userEvent.setEmail(user.getEmail());
        userEvent.setName(user.getFirstName()+" "+ user.getLastName());
        userEvent.setEventType("kyc-verification");
        userEvent.setUserId(user.getUserName());
        userEvent.setRedirectUrl(redirectUrl);
        try {
            emailEventProducer.sendEmailEvent(userEvent);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }


    // Get inquiry status (optional check)
    public String getInquiryStatus(String inquiryId) {
        String url = personaConfig.getBaseUrl() + "/inquiries/" + inquiryId;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(personaConfig.getApiKey());

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

        Map<String, Object> attributes =
                (Map<String, Object>) ((Map<String, Object>) response.getBody().get("data")).get("attributes");

        return (String) attributes.get("status"); // e.g. "completed", "failed"
    }

    // Update verification result from webhook
    public void updateVerificationStatus(String inquiryId, String status) {
        User user = userRepository.findByPersonaInquiryId(inquiryId);
        if (user != null) {
            user.setVerificationStatus(status.toUpperCase()); // COMPLETED or FAILED
            userRepository.save(user);
        }
    }

    /**
     * method to verify signature , in order to verify  whether the call is from persona or not
     * @param payload
     * @param signature
     * @return
     */
    public boolean verifySignature(Map<String, Object> payload, String signature) {
        try {
            String webhookSecret= personaConfig.getWebhookSecret();
            String payloadJson = new ObjectMapper().writeValueAsString(payload);

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(), "HmacSHA256"));
            byte[] hash = mac.doFinal(payloadJson.getBytes());

            String expected = Base64.getEncoder().encodeToString(hash);
            return expected.equals(signature);
        } catch (Exception e) {
            return false;
        }
    }
}


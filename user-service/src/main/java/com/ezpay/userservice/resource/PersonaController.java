package com.ezpay.userservice.resource;

import com.ezpay.userservice.service.PersonaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/kyc")
public class PersonaController {

    private final PersonaService personaService;

    public PersonaController(PersonaService personaService) {
        this.personaService = personaService;
    }

    // Start verification for a user
    @PostMapping("/verification")
    public Map<String, Object> startVerification(@RequestBody Map<String,String> userDetails) {
       String userName = userDetails.get("userName");
        return personaService.startVerification(userName);
    }

    // Webhook callback from Persona
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(@RequestBody Map<String, Object> payload,@RequestHeader("Persona-Signature") String signature) {

        if (!personaService.verifySignature(payload, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }
        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        String inquiryId = (String) data.get("id");
        Map<String, Object> attributes = (Map<String, Object>) data.get("attributes");
        String status = (String) attributes.get("status");
        personaService.updateVerificationStatus(inquiryId, status);
        return ResponseEntity.ok("ok");
    }

    // Manually check inquiry status
    @GetMapping("/status/{inquiryId}")
    public String checkStatus(@PathVariable String inquiryId) {
        return personaService.getInquiryStatus(inquiryId);
    }
}


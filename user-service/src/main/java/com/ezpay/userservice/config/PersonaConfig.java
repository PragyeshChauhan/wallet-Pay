package com.ezpay.userservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class PersonaConfig {

    @Value("${persona.api-key}")
    private String apiKey;

    @Value("${persona.base-url}")
    private String baseUrl;

    @Value("${persona.template-id}")
    private String templateId;

    @Value("${persona.webhookSecret}")
    private String webhookSecret;

//    @Bean
//    public RestTemplate personaRestTemplate() {
//        return new RestTemplate();
//    }

    public String getApiKey() { return apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public String getTemplateId() { return templateId; }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }
}


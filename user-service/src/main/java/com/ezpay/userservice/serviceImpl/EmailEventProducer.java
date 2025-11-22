package com.ezpay.userservice.serviceImpl;

import com.ezpay.userservice.dto.UserEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class EmailEventProducer {
    private static final Logger log = LoggerFactory.getLogger(EmailEventProducer.class);


    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${kafka.topics}")
    private String emailTopic;

    public EmailEventProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendEmailEvent(UserEvent event) throws JsonProcessingException {
        log.info("Inside sendEmailEvent()");
        ObjectMapper objectMapper = new ObjectMapper();
        String eventJson = objectMapper.writeValueAsString(event);// Manual serialization
        kafkaTemplate.send(emailTopic,eventJson);  // Send as String
        log.info("kafka template send method executed()");
    }
}

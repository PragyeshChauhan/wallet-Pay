package com.ezpay.notificationservice.service;

import com.ezpay.notificationservice.dto.UserEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class KafkaConsumer {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private EmailService emailService;

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumer.class);

    /**
     * this method is to consume the event produced by user service to send email notification
     * @param message
     * @throws JsonProcessingException
     */
    @KafkaListener(topics = "email-topic", groupId = "notification-group")
    public void consume(String message) throws JsonProcessingException {
        log.info("Inside kafka consume()");
        ObjectMapper objectMapper = new ObjectMapper();
        UserEvent event = objectMapper.readValue(message, UserEvent.class);
        emailService.sendEmailWithTemplate(event);
        log.info("Triggered email send method");
    }
}

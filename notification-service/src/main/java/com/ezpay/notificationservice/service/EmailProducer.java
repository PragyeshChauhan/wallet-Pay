//package com.ezpay.notificationservice.service;
//
//import com.ezpay.notificationservice.dto.UserEvent;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.kafka.core.KafkaTemplate;
//import org.springframework.stereotype.Service;
//
//@Service
//public class EmailProducer {
//
//    private final KafkaTemplate<String, UserEvent> kafkaTemplate;
//
//    @Value("${kafka.topics.email}")
//    private String emailTopic;
//
//    public EmailProducer(KafkaTemplate<String, UserEvent> kafkaTemplate) {
//        this.kafkaTemplate = kafkaTemplate;
//    }
//
//    public void sendOtpEmail(UserEvent request) {
//        kafkaTemplate.send(emailTopic, request);
//    }
//}

package com.ezpay.frauddetectionservice.services;

import com.ezpay.frauddetectionservice.records.AnomalyEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class AnomalyEventProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnomalyEventProducer.class);
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${kafka.anomaly.topic:anomaly-events}")
    private String topic;

    @Autowired
    public AnomalyEventProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(AnomalyEvent event) {
        try {
            String payload = new ObjectMapper().writeValueAsString(event);
            kafkaTemplate.send(topic, event.getDeviceId(), payload);
            LOGGER.info("AnomalyEvent pushed to Kafka for device {}", event.getDeviceId());
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to serialize AnomalyEvent", e);
        }
    }
}


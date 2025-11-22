package com.ezpay.frauddetectionservice.controllers;

import com.ezpay.frauddetectionservice.records.AnomalyEvent;
import com.ezpay.frauddetectionservice.services.AnomalyEventProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/fraud")
public class FraudServiceController {

    @Autowired
    private AnomalyEventProducer anomalyEventProducer;

    @PostMapping("/anomaly")
    public ResponseEntity<Void> receiveAnomaly(@RequestBody AnomalyEvent event) {
        anomalyEventProducer.send(event);
        return ResponseEntity.ok().build();
    }
}


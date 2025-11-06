package com.ezpay.apigateway.clients;

import com.ezpay.apigateway.model.AnomalyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
public class FraudServiceClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(FraudServiceClient.class);

    private final WebClient webClient;

    public FraudServiceClient(@Value("${fraud.service.base-url:http://localhost:8082}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
        LOGGER.info("FraudServiceClient initialized with base URL: {} at {}", baseUrl, Instant.now());
    }

    public void publishAnomaly(AnomalyEvent event) {
        webClient.post()
                .uri("/internal/fraud/anomaly")
                .bodyValue(event)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> {
                    LOGGER.error("Failed to push anomaly event: HTTP {}", response.statusCode());
                    return Mono.error(new RuntimeException("Fraud service responded with error"));
                })
                .toBodilessEntity()
                .doOnSuccess(response -> LOGGER.info("Anomaly event sent to Fraud Service for deviceId={}", event.getDeviceId()))
                .doOnError(ex -> LOGGER.error("Failed to send anomaly event to Fraud Service", ex))
                .subscribe(); // async
    }
}


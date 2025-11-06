package com.ezpay.apigateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;

/**
 * Reactive health indicator for monitoring API Gateway and dependent services.
 */
@Component
public class GatewayHealthIndicator implements ReactiveHealthIndicator {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayHealthIndicator.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static final List<String> SERVICES_TO_CHECK = Arrays.asList(
            "account-service", "bankintegration-service", "config-server", "creditcard-service",
            "frauddetection-service", "loan-service", "notification-service", "payment-service",
            "transaction-service", "user-service"
    );

    @Value("${eureka.client.service-url.defaultZone}")
    private String eurekaUrl;

    private final WebClient webClient;

    public GatewayHealthIndicator(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<Health> health() {
        LOGGER.debug("Initiating API Gateway health check at {}", java.time.LocalDateTime.now());
        return Mono.zip(checkEureka(), checkMicroservices())
                .map(tuple -> {
                    boolean eurekaHealthy = tuple.getT1();
                    Map<String, Boolean> microserviceHealth = tuple.getT2();

                    Health.Builder builder = (eurekaHealthy && microserviceHealth.values().stream().allMatch(Boolean::booleanValue))
                            ? Health.up()
                            : Health.down();

                    builder.withDetail("eureka", eurekaHealthy ? "Available" : "Unavailable");
                    microserviceHealth.forEach((service, healthy) ->
                            builder.withDetail(service, healthy ? "Available" : "Unavailable"));

                    Health health = builder.build();
                    LOGGER.info("API Gateway health status: {} at {}", health.getStatus(), java.time.LocalDateTime.now());
                    return health;
                })
                .onErrorResume(ex -> {
                    LOGGER.error("Health check failed: {} at {}", ex.getMessage(), java.time.LocalDateTime.now(), ex);
                    return Mono.just(Health.down()
                            .withDetail("error", ex.getMessage())
                            .withDetail("message", "Health check failed")
                            .build());
                });
    }

    private Mono<Boolean> checkEureka() {
        String healthUrl = eurekaUrl.replace("/eureka", "/actuator/health");
        return webClient.get()
                .uri(healthUrl)
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> response.contains("\"status\":\"UP\""))
                .timeout(TIMEOUT)
                .doOnSuccess(result -> LOGGER.debug("Eureka health: {} at {}", result ? "Healthy" : "Unhealthy", java.time.LocalDateTime.now()))
                .onErrorResume(ex -> {
                    LOGGER.warn("Eureka health check failed: {} at {}", ex.getMessage(), java.time.LocalDateTime.now());
                    return Mono.just(false);
                })
                .defaultIfEmpty(false);
    }

    private Mono<Map<String, Boolean>> checkMicroservices() {
        List<Mono<Boolean>> healthChecks = SERVICES_TO_CHECK.stream()
                .map(this::checkMicroservice)
                .toList();

        return Mono.zip(healthChecks, results -> {
            Map<String, Boolean> healthStatus = new HashMap<>();
            for (int i = 0; i < results.length; i++) {
                healthStatus.put(SERVICES_TO_CHECK.get(i), (Boolean)results[i]);
            }
            return healthStatus;
        });
    }

    private Mono<Boolean> checkMicroservice(String serviceId) {
        String eurekaAppsUrl = eurekaUrl + "/eureka/apps/" + serviceId;
        return webClient.get()
                .uri(eurekaAppsUrl)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(response -> {
                    String instanceUrl = extractInstanceUrl(response);
                    if (instanceUrl == null) {
                        LOGGER.warn("No instance found for service: {} at {}", serviceId, java.time.LocalDateTime.now());
                        return Mono.just(false);
                    }
                    String healthUrl = instanceUrl + "/actuator/health";
                    return webClient.get()
                            .uri(healthUrl)
                            .retrieve()
                            .bodyToMono(String.class)
                            .map(healthResponse -> healthResponse.contains("\"status\":\"UP\""))
                            .timeout(TIMEOUT)
                            .doOnSuccess(result -> LOGGER.debug("{} health: {} at {}", serviceId, result ? "Healthy" : "Unhealthy", java.time.LocalDateTime.now()))
                            .onErrorResume(ex -> {
                                LOGGER.warn("{} health check failed: {} at {}", serviceId, ex.getMessage(), java.time.LocalDateTime.now());
                                return Mono.just(false);
                            })
                            .defaultIfEmpty(false);
                })
                .onErrorResume(ex -> {
                    LOGGER.warn("Failed to fetch {} from Eureka: {} at {}", serviceId, ex.getMessage(), java.time.LocalDateTime.now());
                    return Mono.just(false);
                });
    }

    private String extractInstanceUrl(String eurekaResponse) {
        if (eurekaResponse.contains("<instance>")) {
            int hostStart = eurekaResponse.indexOf("<hostName>") + 10;
            int hostEnd = eurekaResponse.indexOf("</hostName>");
            String host = eurekaResponse.substring(hostStart, hostEnd);

            int portStart = eurekaResponse.indexOf("<port enabled=\"true\">") + 17;
            int portEnd = eurekaResponse.indexOf("</port>");
            String port = eurekaResponse.substring(portStart, portEnd);

            return "http://" + host + ":" + port;
        }
        return null;
    }
}

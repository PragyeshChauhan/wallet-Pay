package com.ezpay.apigateway.controller;

import com.ezpay.apigateway.model.records;
import com.ezpay.apigateway.exception.ApiException;
import com.ezpay.apigateway.service.ValidationService;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class GatewayController {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayController.class);
    private static final String X_API_KEY_HEADER = "X-Api-Key";

    private final WebClient webClient;
    private final ValidationService validationService;
    private final MeterRegistry meterRegistry;

    @Value("${url.service.auth}")
    private String authServiceUrl;

    @Value("${url.service.user}")
    private String userServiceUrl;

    @Value("${gateway.api.key:dfcvgbhnjmk353647gwsstevdge}")
    private String apiKey;

    private String resolvedApiKey = "dfcvgbhnjmk353647gwsstevdge";

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.contains("vault")) {
            this.resolvedApiKey = System.getenv("GATEWAY_API_KEY"); // fallback
            LOGGER.warn("API Key loaded from fallback environment variable.");
        } else {
            this.resolvedApiKey = apiKey;
        }
    }

    @Autowired
    public GatewayController(WebClient webClient, ValidationService  validationService, MeterRegistry meterRegistry) {
        this.webClient = webClient;
        this.validationService = validationService;
        this.meterRegistry = meterRegistry;
    }

    @PostMapping("/refresh")
    @Retry(name = "gatewayRefresh")
    public Mono<ResponseEntity<Map<String, Object>>> refresh(@RequestHeader("DPoP") String dpopHeader,
                                                             @RequestHeader("DeviceId") String deviceId,
                                                             @RequestHeader("RefreshToken") String refreshToken,
                                                             @RequestHeader(X_API_KEY_HEADER) String clientApiKey) {
        validateApiKey(clientApiKey);
        meterRegistry.counter("gateway.refresh.requests", "device", deviceId).increment();
        LOGGER.info("Refresh request for device: {} at {}", deviceId, LocalDateTime.now());
        URI path= URI.create("/api/auth/refresh");

        return webClient.method(HttpMethod.POST)
                .uri(authServiceUrl + path)
                .header("RefreshToken", refreshToken)
                .header("DeviceId", deviceId)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue(BodyInserters.empty())
                .exchangeToMono(clientResponse -> {
                    if (!clientResponse.statusCode().is2xxSuccessful()) {
                        LOGGER.info("Refresh request Failed  for device: {} at {}", deviceId, LocalDateTime.now());
                    }
                    return clientResponse.bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                            .map(body -> ResponseEntity
                                    .status(clientResponse.statusCode())
                                    .headers(httpHeaders -> httpHeaders.addAll(clientResponse.headers().asHttpHeaders()))
                                    .body(body));
                })
                .doOnSuccess(r -> meterRegistry.counter("gateway.proxy.success").increment())
                .doOnError(e -> meterRegistry.counter("gateway.proxy.errors", "reason", e.getMessage()).increment())
                .subscribeOn(Schedulers.parallel())
                .onErrorResume(e -> Mono.
                        just(handleError(e, "Token Generation failed as Auth Service Not Available", deviceId, null))

                );
    }

    @GetMapping("/nonce")
    public Mono<ResponseEntity<String>> getNonce(@RequestParam String deviceId,
                                                 @RequestHeader(X_API_KEY_HEADER) String clientApiKey) {
        validateApiKey(clientApiKey);
        meterRegistry.counter("gateway.nonce.requests", "device", deviceId).increment();
        LOGGER.info("Generating nonce for device: {} at {}", deviceId, LocalDateTime.now());
        String nonce = validationService.generateNonce(deviceId);
        return Mono.just(ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(nonce));
    }



    private void validateApiKey(String clientApiKey) {
        if (!resolvedApiKey.equals(clientApiKey)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid API key");
        }
    }



//    private ResponseEntity<Map<String, Object>> buildSuccessResponse(String message, records.TokenResponse tokens, Object request, String deviceId) {
//        Map<String, Object> response = new HashMap<>();
//        response.put("message", message);
//        if (request instanceof records.SignupRequest signup) {
//            response.put("username", signup.username());
//            response.put("email", signup.email());
//        } else if (request instanceof records.LoginRequest login) {
//            response.put("username", login.username());
//        }
//        response.put("access_token", tokens.accessToken());
//        response.put("refresh_token", tokens.refreshToken());
//        response.put("expires_in", tokens.expiresIn());
//        LOGGER.info("{} for {} at {}", message, request != null ? request.toString() : deviceId, LocalDateTime.now());
//        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
//    }

    private ResponseEntity<Map<String, Object>> handleError(Throwable e, String errorMsg, String deviceId, Object request) {
        LOGGER.error("{} for device: {} at {} IST: {}", errorMsg, deviceId, LocalDateTime.now(), e.getMessage(), e);
        meterRegistry.counter("gateway.errors", "type", errorMsg, "device", deviceId, "request", request != null ? request.toString() : "null").increment();
        Map<String, Object> errorResponse = new HashMap<>();
        if (request instanceof records.SignupRequest signup) {
            errorResponse.put("mobile", signup.mobile());
        } else if (request instanceof records.LoginRequest login) {
            errorResponse.put("username", login.username());
        }
        errorResponse.put("error", errorMsg);
        errorResponse.put("message", e.getMessage());
        errorResponse.put("request", request != null ? request : "N/A");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(errorResponse);
    }
}

package com.ezpay.apigateway.filter;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.ezpay.apigateway.model.ValidationResult;
import com.ezpay.apigateway.service.RateLimiterService;
import com.ezpay.apigateway.service.ValidationService;
import com.ezpay.apigateway.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component// After JWT filter, before routing
public class DpopValidationWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(DpopValidationWebFilter.class);
    public static final String ATTR_DECODED_JWT = "decodedJwt";

    @Value("${security.dpop.validation.enabled:false}")
    private boolean dpopValidationEnabled;

    @Value("${rate-limit.threshold:6000}")
    private long rateLimitThreshold;

    @Value("${rate-limit.ttl-seconds:60}")
    private long rateLimitTtlSeconds;

    private final ValidationService validationService;
    private final RateLimiterService rateLimiterService;

    public DpopValidationWebFilter(ValidationService validationService,
                                   RateLimiterService rateLimiterService) {
        this.validationService = validationService;
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!dpopValidationEnabled) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        DecodedJWT decodedJwt = exchange.getAttribute(ATTR_DECODED_JWT);
        String jwtToken = decodedJwt != null ? decodedJwt.getToken() : extractJwtFromHeader(request);
        String dpopHeader = request.getHeaders().getFirst("DPoP");
        String deviceId = request.getHeaders().getFirst("X-Device-Id");

        // Rate limiting
        if (!rateLimiterService.allowRequest(deviceId, rateLimitThreshold, rateLimitTtlSeconds)) {
            return ResponseUtil.setErrorResponse(exchange, HttpStatus.TOO_MANY_REQUESTS, "rate_limit_exceeded", "Rate limit exceeded for DPoP validations");
        }

        // Call validation service
        ValidationResult result = validationService.validateDpop(
                decodedJwt != null ? decodedJwt : validationService.verifyToken(jwtToken),
                dpopHeader,
                request.getMethod(),
                request.getURI().toString(),
                deviceId
        );

        if (!result.success()) {
            log.warn("DPoP validation failed | deviceId={} | status={} | code={} | message={}",
                    deviceId, result.status(), result.errorCode(), result.message());
            return ResponseUtil.setErrorResponse(exchange, result.status(), result.errorCode(), result.message());
        }

        log.debug("DPoP validation successful | deviceId={}", deviceId);
        return chain.filter(exchange);
    }

    private String extractJwtFromHeader(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return (authHeader != null && authHeader.startsWith("Bearer ")) ? authHeader.substring(7) : null;
    }
}
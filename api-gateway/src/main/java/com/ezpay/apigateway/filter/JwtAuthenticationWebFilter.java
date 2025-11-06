package com.ezpay.apigateway.filter;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.ezpay.apigateway.exception.ApiException;
import com.ezpay.apigateway.service.RateLimiterService;
import com.ezpay.apigateway.service.ValidationService;
import com.ezpay.apigateway.config.KeyStoreConfig;
import com.ezpay.apigateway.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class JwtAuthenticationWebFilter implements WebFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationWebFilter.class);
    public static final String ATTR_DECODED_JWT = "decodedJwt";

    @Value("${auth.bypass-paths:/api/user/login,/api/otp/**,/api/login,/actuator/**,/fallback,/swagger-ui.html,/api-docs/**}")
    private String[] bypassPaths;

    @Value("${token.cache.ttl-seconds:3600}")
    private long tokenCacheTtlSeconds;

    @Value("${token.cache.key-prefix:token:cache:}")
    private String tokenCacheKeyPrefix;

    @Value("${dev.skip-mtls-validation:false}")
    private boolean skipMtlsValidation;

    @Value("${rate-limit.threshold:6000}")
    private long rateLimitThreshold;

    @Value("${rate-limit.ttl-seconds:60}")
    private long rateLimitTtlSeconds;

    private final ValidationService validationService;
    private final RateLimiterService rateLimiterService;
    private final RedisTemplate<String, String> redisTemplate;
    private final KeyStoreConfig keyStoreConfig;

    private final AtomicLong successfulAuths = new AtomicLong();
    private final AtomicLong failedAuths = new AtomicLong();

    public JwtAuthenticationWebFilter(ValidationService validationService,
                                      RateLimiterService rateLimiterService,
                                      RedisTemplate<String, String> redisTemplate,
                                      KeyStoreConfig keyStoreConfig) {
        this.validationService = validationService;
        this.rateLimiterService = rateLimiterService;
        this.redisTemplate = redisTemplate;
        this.keyStoreConfig = keyStoreConfig;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().toString();
        LOGGER.debug("JwtAuthenticationWebFilter processing request for path: {}", path);
        // Bypass certain public paths
        for (String p : bypassPaths) {
            if (path.matches(p.replace("/**", ".*"))) {
                return chain.filter(exchange);
            }
        }

        return authenticateAndProceed(exchange, chain);
    }

    private Mono<Void> authenticateAndProceed(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        LOGGER.debug("Authorization header: {}", authHeader);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseUtil.setErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "invalid_auth_header", "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);
        String cacheKey = tokenCacheKeyPrefix + token;

        // Short-circuit: token validated recently
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey))) {
            try {
                DecodedJWT decoded = validationService.verifyToken(token);
                exchange.getAttributes().put(ATTR_DECODED_JWT, decoded);
                var auth = buildAuthentication(decoded, token);
                SecurityContextImpl ctx = new SecurityContextImpl(auth);
                successfulAuths.incrementAndGet();
                return chain.filter(exchange).contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(ctx)));
            } catch (ApiException e) {
                return handleFailure(exchange, "400", e.getMessage(), e);
            } catch (Exception e) {
                return handleFailure(exchange, "internal_server_error", "Internal server error: " + e.getMessage(), e);
            }
        }

        try {
            // mTLS validation
//            if (!skipMtlsValidation) {
//                var sslInfo = request.getSslInfo();
//                if (sslInfo == null || sslInfo.getPeerCertificates() == null) {
//                    return ResponseUtil.setErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "missing_client_cert", "Client certificate required");
//                }
//                var cert = (X509Certificate) sslInfo.getPeerCertificates()[0];
//                if (!keyStoreConfig.validateCertificate(cert)) {
//                    return ResponseUtil.setErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "invalid_client_cert", "Invalid client certificate");
//                }
//                // Additional mTLS checks (e.g., CN, issuer)
//                if (!validateCertificateAttributes(cert)) {
//                    return ResponseUtil.setErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "invalid_cert_attributes", "Client certificate attributes invalid");
//                }
//            }

            // Rate limiting
            String deviceId = request.getHeaders().getFirst("X-Device-Id");
            if (!rateLimiterService.allowRequest(deviceId, rateLimitThreshold, rateLimitTtlSeconds)) {
                return ResponseUtil.setErrorResponse(exchange, HttpStatus.TOO_MANY_REQUESTS, "rate_limit_exceeded", "Rate limit exceeded");
            }

            // Session token validation (placeholder)
            if (!validateSessionToken(deviceId, token)) {
                return ResponseUtil.setErrorResponse(exchange, HttpStatus.UNAUTHORIZED, "invalid_session", "Invalid session token");
            }

            DecodedJWT decoded = validationService.verifyToken(token);
            exchange.getAttributes().put(ATTR_DECODED_JWT, decoded);
            redisTemplate.opsForValue().set(cacheKey, "1", tokenCacheTtlSeconds, TimeUnit.SECONDS);

            var auth = buildAuthentication(decoded, token);
            SecurityContextImpl ctx = new SecurityContextImpl(auth);
            successfulAuths.incrementAndGet();
            return chain.filter(exchange).contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(ctx)));

        } catch (ApiException e) {
            return handleFailure(exchange, "400", e.getMessage(), e);
        } catch (Exception e) {
            return handleFailure(exchange, "internal_server_error", "Internal server error: " + e.getMessage(), e);
        }
    }

    private boolean validateCertificateAttributes(X509Certificate cert) {
        // Example: Check CN, issuer, or other attributes
        try {
            String cn = cert.getSubjectX500Principal().getName();
            // Add specific validation logic (e.g., check CN against expected values)
            return cn.contains("ezpay.client"); // Placeholder
        } catch (Exception e) {
            LOGGER.warn("Certificate attribute validation failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean validateSessionToken(String deviceId, String token) {
        // Placeholder: Validate session token in Redis or external service
        if (deviceId == null) return false;
        String sessionKey = "session:token:" + deviceId;
        String storedToken = redisTemplate.opsForValue().get(sessionKey);
        return token.equals(storedToken);
    }

    private UsernamePasswordAuthenticationToken buildAuthentication(DecodedJWT jwt, String token) {
        String subject = jwt.getSubject() != null ? jwt.getSubject() : jwt.getClaim("sub").asString();
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        return new UsernamePasswordAuthenticationToken(subject, token, authorities);
    }

    private Mono<Void> handleFailure(ServerWebExchange exchange, String errorCode, String msg, Exception e) {
        failedAuths.incrementAndGet();
        LOGGER.error("Authentication failure at {}: {}", Instant.now(), msg);
        return ResponseUtil.setErrorResponse(exchange, HttpStatus.UNAUTHORIZED, errorCode, msg);
    }
}
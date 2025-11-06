package com.ezpay.apigateway.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.ezpay.apigateway.clients.FraudServiceClient;
import com.ezpay.apigateway.config.KeyStoreConfig;
import com.ezpay.apigateway.model.AnomalyEvent;
import com.ezpay.apigateway.model.DpopParsed;
import com.ezpay.apigateway.model.ValidationResult;
import com.ezpay.apigateway.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class ValidationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValidationService.class);

    // Concurrency primitives & caches
    private static final ReentrantReadWriteLock nonceLock = new ReentrantReadWriteLock();
    private static final ConcurrentHashMap<String, PublicKey> PUBLIC_KEY_CACHE = new ConcurrentHashMap<>();
    private static final AtomicLong REQUEST_COUNTER = new AtomicLong(0);

    // Redis key prefixes & defaults
    private static final String NONCE_PREFIX = "dpop:nonce:";
    private static final String PUBKEY_REDIS_PREFIX = "dpop:pubkey:";
    private static final String JTI_REDIS_PREFIX = "dpop:jti:";

    private final RedisTemplate<String, String> redisTemplate;
    private final RateLimiterService rateLimiterService;
    private final KeyStoreConfig keyStoreConfig;
    private final FraudServiceClient anomalyEventProducer;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${jwt.issuer}")
    private String jwtIssuer;

    @Value("${jwt.audience}")
    private String jwtAudience;

    private final long maxClockSkewSeconds = 300L;

    @Value("${rate-limit.threshold:6000}")
    private long rateLimitThreshold;

    @Value("${rate-limit.ttl-seconds:60}")
    private long rateLimitTtlSeconds;

    public ValidationService(RedisTemplate<String, String> redisTemplate,
                             RateLimiterService rateLimiterService,
                             KeyStoreConfig keyStoreConfig,
                             FraudServiceClient anomalyEventProducer,
                             MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.rateLimiterService = rateLimiterService;
        this.keyStoreConfig = keyStoreConfig;
        this.anomalyEventProducer = anomalyEventProducer;
        this.meterRegistry = meterRegistry;
        LOGGER.info("ValidationService initialized at {}", Instant.now());
    }

    @PostConstruct
    private void validateConfig() {
        if (!StringUtils.hasText(jwtIssuer) || !StringUtils.hasText(jwtAudience)) {
            LOGGER.warn("jwt.issuer or jwt.audience not configured correctly");
        }
    }

    // JWT validation (keeps using RSA public key from keystore)
    public DecodedJWT verifyToken(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "access_token_missing", "Access token is required");
        }
        try {
            DecodedJWT jwt = JWT.require(getJwtAlgorithm())
                    .withIssuer(jwtIssuer)
                    .withAudience(jwtAudience)
                    .acceptLeeway(maxClockSkewSeconds)
                    .build()
                    .verify(accessToken);
            meterRegistry.counter("validation.jwt.success").increment();
            return jwt;
        } catch (com.auth0.jwt.exceptions.JWTVerificationException e) {
            meterRegistry.counter("validation.jwt.failure").increment();
            throw new ApiException(HttpStatus.UNAUTHORIZED, "jwt_verification_failed", "Access token verification failed: " + e.getMessage());
        }
    }

    private Algorithm getJwtAlgorithm() {
        try {
            KeyStore ks = keyStoreConfig.getLoadedTrustStore();
            if (ks == null) throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "keystore_not_loaded", "Keystore not loaded");
            String alias = ks.aliases().nextElement();
            Certificate cert = ks.getCertificate(alias);
            if (cert == null) throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "jwt_key_load_failed", "No certificate for alias: " + alias);
            return Algorithm.RSA256((RSAPublicKey) cert.getPublicKey(), null);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "jwt_key_load_failed", "Failed to load JWT public key: " + e.getMessage());
        }
    }

    // === DPoP validation: single canonical method ===
    @CircuitBreaker(name = "dpopValidation", fallbackMethod = "dpopFallback")
    @Retry(name = "dpopValidation")
    public ValidationResult validateDpop(DecodedJWT accessTokenJwt,
                                         String dpopCompactJws,
                                         HttpMethod httpMethod,
                                         String normalizedHtu,
                                         String deviceId) {
        long requestId = REQUEST_COUNTER.incrementAndGet();
        meterRegistry.counter("validation.dpop.requests", "device", deviceId == null ? "unknown" : deviceId).increment();
        LOGGER.debug("validateDpop requestId={} device={} uri={} at {}", requestId, deviceId, normalizedHtu, Instant.now());

        if (!StringUtils.hasText(dpopCompactJws)) {
            return ValidationResult.failure(HttpStatus.BAD_REQUEST, "missing_dpop_header", "DPoP header is required");
        }

        //  device trust check
        if (!isRequestAuthenticated(deviceId)) {
            return ValidationResult.failure(HttpStatus.FORBIDDEN, "unauthenticated_device", "Device not authenticated");
        }

        // Rate limiting
        if (!rateLimiterService.allowRequestWithDpopValidation(deviceId, rateLimitThreshold, rateLimitTtlSeconds)) {
            return ValidationResult.failure(HttpStatus.TOO_MANY_REQUESTS, "dpop_rate_limited", "Rate limit exceeded for DPoP validation");
        }

        // 1) Parse & verify DPoP JWS
        DpopParsed parsed;
        try {
            parsed = parseAndVerifyDpopJws(dpopCompactJws);
        } catch (ApiException e) {
            return ValidationResult.failure(e.getStatus(),"403", e.getMessage());
        }

        // 2) iat freshness (allow some skew)
        Instant iat = parsed.getIat();
        if (iat == null || Instant.now().minusSeconds(maxClockSkewSeconds).isAfter(iat)) {
            return ValidationResult.failure(HttpStatus.FORBIDDEN, "dpop_bad_iat", "DPoP proof iat invalid/expired");
        }

        // 3) jti replay protection
        String jti = parsed.getJti();
        long jtiTtlSeconds = 900L;
        if (!recordJtiIfNew(jti, jtiTtlSeconds)) {
            return ValidationResult.failure(HttpStatus.FORBIDDEN, "dpop_jti_replay", "DPoP jti replay detected");
        }

        // 4) htm + htu check
        if (!httpMethod.name().equalsIgnoreCase(parsed.getHtm()) || !normalizedHtu.equals(parsed.getHtu())) {
            return ValidationResult.failure(HttpStatus.FORBIDDEN, "dpop_htm_htu_mismatch", "DPoP method/URI mismatch");
        }

        // 5) nonce verification
        String nonce = parsed.getNonce();
        if (nonce != null) {
            String nonceKey = NONCE_PREFIX + deviceId + "_" + nonce;
            String storedNonce = redisTemplate.opsForValue().get(nonceKey);
            if (storedNonce == null || !storedNonce.equals(nonce)) {
                return ValidationResult.failure(HttpStatus.FORBIDDEN, "dpop_invalid_nonce", "Invalid DPoP nonce");
            }
        } else {
            return ValidationResult.failure(HttpStatus.FORBIDDEN, "dpop_missing_nonce", "DPoP nonce required");
        }

        // 6) token-binding (cnf.jkt)
        try {
            verifyTokenBinding(accessTokenJwt, parsed.getJwkJson());
        } catch (ApiException e) {
            return ValidationResult.failure(e.getStatus(), "403", e.getMessage());
        }

        // 7) cache public key (redis + in-memory)
        cachePublicKeyIfAbsent(deviceId, parsed.getThumbprint(), parsed.getJwkJson());

        // 8) rotate nonce
        rotateNonce(deviceId, nonce);

        meterRegistry.counter("validation.dpop.success").increment();
        auditLog(requestId, deviceId, normalizedHtu, "dpop_ok");
        return ValidationResult.buildSuccess();
    }



    private DpopParsed parseAndVerifyDpopJws(String compactJws) {
        try {
            // Parse JWS
            JWSObject jws = JWSObject.parse(compactJws);

            // Extract JWK from header or payload
            JWK jwk = null;
            Object jwkHeader = jws.getHeader().toJSONObject().get("jwk");
            if (jwkHeader != null) {
                jwk = JWK.parse(jwkHeader.toString());
            }

            // Parse payload into claims map
            Map<String, Object> payload = objectMapper.readValue(jws.getPayload().toString(), Map.class);

            if (jwk == null && payload.containsKey("jwk")) {
                jwk = JWK.parse(objectMapper.writeValueAsString(payload.get("jwk")));
            }

            if (jwk == null) {
                throw new ApiException(HttpStatus.FORBIDDEN, "dpop_missing_jwk", "DPoP proof missing jwk");
            }

            // Choose verifier based on key type
            JWSVerifier verifier;
            if (jwk instanceof RSAKey rsaKey) {
                verifier = new RSASSAVerifier(rsaKey.toRSAPublicKey());
            } else if (jwk instanceof ECKey ecKey) {
                verifier = new ECDSAVerifier(ecKey.toECPublicKey());
            } else {
                throw new ApiException(HttpStatus.FORBIDDEN, "dpop_unsupported_key", "Unsupported DPoP key type");
            }

            // Verify signature
            if (!jws.verify(verifier)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "dpop_signature_failed", "DPoP signature verification failed");
            }

            // Compute key details
            String thumb = jwk.computeThumbprint().toString();
            String jwkJson = jwk.toJSONString();
            String nonce = payload.containsKey("nonce") ? String.valueOf(payload.get("nonce")) : null;

            // Build immutable DTO
            return new DpopParsed.Builder()
                    .claims(payload)
                    .jwkJson(jwkJson)
                    .thumbprint(thumb)
                    .nonce(nonce)
                    .iatFromEpoch(payload.get("iat"))
                    .build();

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "dpop_invalid_proof",
                    "Invalid DPoP proof: " + e.getMessage());
        }
    }


    // Token-binding
    private void verifyTokenBinding(DecodedJWT token, String dpopJwkJson) {
        try {
            Map<String, Object> cnf = token.getClaim("cnf").asMap();
            if (cnf == null || !cnf.containsKey("jkt")) {
                throw new ApiException(HttpStatus.FORBIDDEN, "missing_cnf_jkt", "Access token missing cnf.jkt");
            }
            String tokenJkt = cnf.get("jkt").toString();
            String proofThumbprint = JWK.parse(dpopJwkJson).computeThumbprint().toString();
            if (!tokenJkt.equals(proofThumbprint)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "dpop_token_binding_mismatch", "DPoP key thumbprint does not match token binding");
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "token_binding_failed", "Failed to verify token binding: " + e.getMessage());
        }
    }

    // JTI replay protection
    private boolean recordJtiIfNew(String jti, long ttlSeconds) {
        if (!StringUtils.hasText(jti)) throw new ApiException(HttpStatus.BAD_REQUEST, "dpop_missing_jti", "DPoP proof missing jti");
        String key = JTI_REDIS_PREFIX + jti;
        Boolean set = redisTemplate.opsForValue().setIfAbsent(key, "1", ttlSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(set);
    }

    // Public key caching
    private void cachePublicKeyIfAbsent(String deviceId, String thumbprint, String jwkJson) {
        try {
            String redisKey = PUBKEY_REDIS_PREFIX + (deviceId == null ? "unknown" : deviceId) + "_" + thumbprint;
            String existing = redisTemplate.opsForValue().get(redisKey);
            if (existing == null) {
                long pubkeyTtlHours = 24L;
                redisTemplate.opsForValue().set(redisKey, jwkJson, pubkeyTtlHours, TimeUnit.HOURS);
            }
            PublicKey pub = PUBLIC_KEY_CACHE.get(redisKey);
            if (pub == null) {
                JWK jwk = JWK.parse(jwkJson);
                if (jwk instanceof RSAKey) PUBLIC_KEY_CACHE.put(redisKey, ((RSAKey) jwk).toRSAPublicKey());
                else if (jwk instanceof ECKey) PUBLIC_KEY_CACHE.put(redisKey, ((ECKey) jwk).toECPublicKey());
            }
        } catch (Exception e) {
            LOGGER.warn("cachePublicKeyIfAbsent failed: {}", e.getMessage());
        }
    }

    // Nonce helpers
    public String generateNonce(String deviceId) {
        nonceLock.writeLock().lock();
        try {
            String nonce = UUID.randomUUID().toString();
            long nonceTtlSeconds = 300L;
            redisTemplate.opsForValue().set(NONCE_PREFIX + deviceId + "_" + nonce, nonce, nonceTtlSeconds, TimeUnit.SECONDS);
            return nonce;
        } finally {
            nonceLock.writeLock().unlock();
        }
    }

    private void rotateNonce(String deviceId, String oldNonce) {
        nonceLock.writeLock().lock();
        try {
            redisTemplate.delete(NONCE_PREFIX + deviceId + "_" + oldNonce);
        } finally {
            nonceLock.writeLock().unlock();
        }
    }

    // Keystore sync, anomaly, audit
    @Scheduled(fixedRate = 3 * 60 * 60 * 1000)
    public void syncKeyStoreWithConfig() {
        keyStoreConfig.reloadCertificatesIfNeeded();
    }

    private void publishAnomaly(long reqId, String reason, String deviceId, String uri) {
        try {
            anomalyEventProducer.publishAnomaly(new AnomalyEvent(reqId, reason, deviceId, uri, Instant.now()));
        } catch (Exception e) {
            LOGGER.error("publishAnomaly failed: {}", e.getMessage());
        }
        meterRegistry.counter("validation.anomaly", "reason", reason).increment();
    }

    private boolean isRequestAuthenticated(String deviceId) {
        if (deviceId == null) return false;
        String trust = redisTemplate.opsForValue().get("trust:" + deviceId);
        return trust != null && Boolean.parseBoolean(trust);
    }

    private void auditLog(long requestId, String deviceId, String httpUri, String status) {
        String entry = String.format("{\"requestId\":%d,\"deviceId\":\"%s\",\"uri\":\"%s\",\"status\":\"%s\",\"timestamp\":\"%s\"}",
                requestId, deviceId == null ? "unknown" : deviceId, httpUri, status, Instant.now());
        try {
            redisTemplate.opsForValue().set("audit:log:" + requestId, entry, 30, TimeUnit.DAYS);
        } catch (Exception e) {
            LOGGER.warn("Failed to write audit log: {}", e.getMessage());
        }
    }

    // Circuit-breaker fallback
    private ValidationResult dpopFallback(Throwable t) {
        long requestId = REQUEST_COUNTER.get();
        publishAnomaly(requestId, "service_unavailable", null, "dpop_fallback");
        return ValidationResult.failure(HttpStatus.SERVICE_UNAVAILABLE, "service_unavailable", "DPoP validation service unavailable");
    }
}

package com.ezpay.apigateway.service;

import com.ezpay.apigateway.clients.FraudServiceClient;
import com.ezpay.apigateway.model.AnomalyEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate limiter with Redis + Kafka-based anomaly reporting.
 * Uses AnomalyEventProducer for forwarding suspicious activity.
 */
@Service
public class RateLimiterService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimiterService.class);
    private static final String RATE_LIMIT_KEY_PREFIX = "rate:limit:";
    private static final AtomicLong REQUEST_COUNTER = new AtomicLong(0);

    private final RedisTemplate<String, String> redisTemplate;
    private final MeterRegistry meterRegistry;
    private final FraudServiceClient anomalyEventProducer;
    private final Counter rateLimitExceededCounter;

    @Value("${rate.limit.default.threshold:5000}")
    private long defaultThreshold;

    @Value("${rate.limit.default.ttl.seconds:60}")
    private long defaultTtlSeconds;

    @Value("${rate.limit.enforce.security:true}")
    private boolean enforceSecurity;

    public RateLimiterService(
            RedisTemplate<String, String> redisTemplate,
            MeterRegistry meterRegistry,
            FraudServiceClient  anomalyEventProducer
    ) {
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
        this.anomalyEventProducer = anomalyEventProducer;
        this.rateLimitExceededCounter = meterRegistry.counter("rate.limit.exceeded", "type", "total");
        LOGGER.info("RateLimiterService initialized at {}", Instant.now());
    }

    public boolean allowRequest(String deviceId, long threshold, long ttlSeconds) {
        long requestId = REQUEST_COUNTER.incrementAndGet();
        String rateKey = RATE_LIMIT_KEY_PREFIX + deviceId;

        long effectiveThreshold = threshold > 0 ? threshold : defaultThreshold;
        long effectiveTtl = ttlSeconds > 0 ? ttlSeconds : defaultTtlSeconds;

        Long count = redisTemplate.opsForValue().increment(rateKey);
        if (count != null && count == 1) {
            redisTemplate.expire(rateKey, effectiveTtl, TimeUnit.SECONDS);
        }

        boolean isAllowed = count == null || count <= effectiveThreshold;
        meterRegistry.counter("rate.limit.requests", "device", deviceId).increment();

        if (!isAllowed) {
            rateLimitExceededCounter.increment();
            LOGGER.warn("Rate limit exceeded: reqId={}, device={}, count={}, threshold={}, time={}",
                    requestId, deviceId, count, effectiveThreshold, Instant.now());

            if (enforceSecurity) {
                publishAnomalyEvent(deviceId, requestId, "Rate limit exceeded");
            }
        } else {
            LOGGER.debug("Rate check passed: reqId={}, device={}, count={}, threshold={}, time={}",
                    requestId, deviceId, count, effectiveThreshold, Instant.now());
        }

        return isAllowed;
    }

    public boolean allowRequestWithDpopValidation(String deviceId, long threshold, long ttlSeconds) {
        long requestId = REQUEST_COUNTER.incrementAndGet();
        String rateKey = RATE_LIMIT_KEY_PREFIX + deviceId;

        long effectiveThreshold = threshold > 0 ? threshold : defaultThreshold;
        long effectiveTtl = ttlSeconds > 0 ? ttlSeconds : defaultTtlSeconds;

        Long count = redisTemplate.opsForValue().increment(rateKey);
        if (count != null && count == 1) {
            redisTemplate.expire(rateKey, effectiveTtl, TimeUnit.SECONDS);
        }

        boolean isAllowed = count == null || count <= effectiveThreshold;
        meterRegistry.counter("rate.limit.dpop.requests", "device", deviceId).increment();

        if (!isAllowed) {
            rateLimitExceededCounter.increment();
            LOGGER.warn("DPoP Rate limit exceeded: reqId={}, device={}, count={}, threshold={}, time={}",
                    requestId, deviceId, count, effectiveThreshold, Instant.now());

            if (enforceSecurity) {
                publishAnomalyEvent(deviceId, requestId, "DPoP rate limit exceeded");
            }
        } else {
            LOGGER.debug("DPoP rate check passed: reqId={}, device={}, count={}, threshold={}, time={}",
                    requestId, deviceId, count, effectiveThreshold, Instant.now());
        }

        return isAllowed;
    }

    private void publishAnomalyEvent(String deviceId, long requestId, String reason) {
        AnomalyEvent event = new AnomalyEvent();
        event.setDeviceId(deviceId);
        event.setRequestId(requestId);
        event.setReason(reason);
        event.setTimestamp(Instant.now());

        anomalyEventProducer.publishAnomaly(event);
        LOGGER.info("AnomalyEvent sent to Kafka for device={} reason='{}'", deviceId, reason);
    }
}

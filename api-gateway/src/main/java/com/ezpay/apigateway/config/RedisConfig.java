package com.ezpay.apigateway.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@Configuration
public class RedisConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisConfig.class);

    @Value("${redis.host:localhost}")
    private String redisHost;

    @Value("${redis.port:6379}")
    private int redisPort;

    @Value("${redis.password:}")
    private String redisPassword;

    @Value("${redis.timeout:60000}")
    private int redisTimeout;

    private final MeterRegistry meterRegistry;

    public RedisConfig(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        LOGGER.info("RedisConfig initialized at {} IST", Instant.now());
    }

    // Standalone Redis config with optional password
    private RedisStandaloneConfiguration buildStandaloneConfig() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost, redisPort);
        if (!redisPassword.isBlank()) {
            config.setPassword(redisPassword);
        }
        return config;
    }

    // Lettuce client config with optional SSL
    private LettuceClientConfiguration buildClientConfig(boolean useSsl) {
        SocketOptions socketOptions = SocketOptions.builder()
                .connectTimeout(Duration.ofMillis(redisTimeout))
                .build();

        ClientOptions clientOptions = ClientOptions.builder()
                .socketOptions(socketOptions)
                .build();

        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder =
                LettuceClientConfiguration.builder()
                        .clientOptions(clientOptions)
                        .commandTimeout(Duration.ofMillis(redisTimeout));

        if (useSsl) {
            builder.useSsl();
        }

        return builder.build();
    }

    // Production Redis connection: secured with SSL
    @Bean
    @Profile("!dev")
    public RedisConnectionFactory prodRedisConnectionFactory() {
        LOGGER.info("Creating RedisConnectionFactory with SSL for PRODUCTION");
        return new LettuceConnectionFactory(buildStandaloneConfig(), buildClientConfig(true));
    }

    // Development Redis connection: insecure, local only
    @Profile("dev")
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", 6379));
    }

    // RedisTemplate used across services
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        template.setEnableTransactionSupport(true);
        template.afterPropertiesSet();

        LOGGER.info("RedisTemplate initialized [host={}, port={}] at {}", redisHost, redisPort, Instant.now());
        meterRegistry.counter("redis.config.initializations", "status", "success").increment();
        return template;
    }

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
    }

    @Bean
    public RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(1, 2);
    }

    @Bean
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(ReactiveRedisConnectionFactory factory) {
        return new ReactiveStringRedisTemplate(factory);
    }
}


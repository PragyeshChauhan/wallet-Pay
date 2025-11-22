package com.ezpay.wallet.auth_service.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

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
    @Bean
    @Profile("dev")
    public RedisConnectionFactory redisConnectionFactory() {
        LOGGER.info("Creating RedisConnectionFactory for DEVELOPMENT");
        return new LettuceConnectionFactory(buildStandaloneConfig(), buildClientConfig(false));
//        return new LettuceConnectionFactory(new RedisStandaloneConfiguration("redis-auth", 6379));
    }

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
        return template;
    }
}
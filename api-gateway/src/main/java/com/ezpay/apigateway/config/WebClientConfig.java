package com.ezpay.apigateway.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.SslContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.reactive.LoadBalancedExchangeFilterFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.logging.AdvancedByteBufFormat;

import java.time.Duration;
import java.time.Instant;

@Configuration
public class WebClientConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebClientConfig.class);

    @Value("${webclient.timeout:10}")
    private int timeoutSeconds;

    @Value("${webclient.tls.protocols:TLSv1.3,TLSv1.2}")
    private String[] tlsProtocols;

    private final MeterRegistry meterRegistry;
    private final LoadBalancedExchangeFilterFunction loadBalancerFilter;

    public WebClientConfig(MeterRegistry meterRegistry, LoadBalancedExchangeFilterFunction loadBalancerFilter) {
        this.meterRegistry = meterRegistry;
        this.loadBalancerFilter = loadBalancerFilter;
        LOGGER.info("WebClientConfig initialized at {} IST", Instant.now());
    }

    private WebClient buildSecureWebClient() {
        HttpClient httpClient = HttpClient.create()
                .secure(sslSpec -> {
                    try {
                        sslSpec.sslContext(SslContextBuilder.forClient()
                                .protocols(tlsProtocols)
                                .build());
                    } catch (Exception e) {
                        LOGGER.error("Failed to configure TLS for WebClient: {}", e.getMessage());
                        throw new IllegalStateException("WebClient TLS configuration failed", e);
                    }
                })
                .responseTimeout(Duration.ofSeconds(timeoutSeconds))
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) Duration.ofSeconds(timeoutSeconds).toMillis())
                .wiretap("reactor.netty.http.client.HttpClient", LogLevel.DEBUG, AdvancedByteBufFormat.TEXTUAL);

        return WebClient.builder()
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .filter(loadBalancerFilter) // <-- Enable service discovery via Eureka
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer {jwt}")
                .defaultHeader("X-Device-Id", "{deviceId}")
                // .defaultHeader("DPoP", "{dpop}")
                .filter((request, next) -> {
                    meterRegistry.counter("webclient.requests", "method", request.method().name(), "url", request.url().toString()).increment();
                    return next.exchange(request);
                })
                .build();
    }

    @Bean
    public WebClient secureWebClient() {
        WebClient webClient = buildSecureWebClient();
        meterRegistry.counter("webclient.initializations", "status", "success").increment();
        LOGGER.info("Secure WebClient initialized with TLS protocols {} at {} IST", String.join(",", tlsProtocols), Instant.now());
        return webClient;
    }
}

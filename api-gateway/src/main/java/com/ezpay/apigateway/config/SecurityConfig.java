 package com.ezpay.apigateway.config;

import com.ezpay.apigateway.filter.DpopValidationWebFilter;
import com.ezpay.apigateway.filter.JwtAuthenticationWebFilter;
import com.ezpay.apigateway.util.ResponseUtil;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.WebFilter;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${cors.allowed-origins:http://localhost:8083}")
    private String allowedOrigins;

    @Value("${rate-limit.threshold:6000}")
    private long rateLimitThreshold;

    @Value("${rate-limit.ttl-seconds:60}")
    private long rateLimitTtlSeconds;

    @Value("${dev.skip-mtls-validation:false}")
    private boolean skipMtlsValidation;

    private final KeyStoreConfig keyStoreConfig;
    private final MeterRegistry meterRegistry;
    private final Map<String, Bucket> rateLimitBuckets = new ConcurrentHashMap<>();
    private final JwtAuthenticationWebFilter jwtAuthenticationFilter;
    private final DpopValidationWebFilter dpopValidationFilter;

    public SecurityConfig(KeyStoreConfig keyStoreConfig,
                          MeterRegistry meterRegistry,
                          JwtAuthenticationWebFilter jwtAuthenticationFilter,
                          DpopValidationWebFilter dpopValidationFilter) {
        this.keyStoreConfig = keyStoreConfig;
        this.meterRegistry = meterRegistry;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.dpopValidationFilter = dpopValidationFilter;
        LOGGER.info("SecurityConfig initialized at {}", Instant.now());
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(
                                "/api/auth/**", "/api/login", "/api/refresh","/api/otp/**","/api/user/login",
                                "/actuator/**", "/fallback", "/swagger-ui.html", "/api-docs/**")
                        .permitAll()
                        .anyExchange().authenticated()
                )
                .headers(headers -> headers
                        .hsts(hsts -> hsts.includeSubdomains(true).maxAge(Duration.ofDays(365)))
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
                        .xssProtection(ServerHttpSecurity.HeaderSpec.XssProtectionSpec::disable)
                        .contentTypeOptions(ServerHttpSecurity.HeaderSpec.ContentTypeOptionsSpec::disable)
                        .frameOptions(ServerHttpSecurity.HeaderSpec.FrameOptionsSpec::disable)
                )
                .addFilterAt(rateLimitFilter(), SecurityWebFiltersOrder.FIRST)
                .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .addFilterAfter(dpopValidationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of(HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(), HttpMethod.DELETE.name()));
        config.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, "DPoP", "X-Device-Id"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    @Profile("dev")
    public CorsConfigurationSource devCorsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:8083"));
        config.setAllowedMethods(List.of(HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(), HttpMethod.DELETE.name()));
        config.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, "DPoP", "X-Device-Id"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public WebFilter rateLimitFilter() {
        Bandwidth limit = Bandwidth.classic(rateLimitThreshold, Refill.greedy(rateLimitThreshold, Duration.ofSeconds(rateLimitTtlSeconds)));
        return (exchange, chain) -> {
            String deviceId = exchange.getRequest().getHeaders().getFirst("X-Device-Id");
            String clientIp = exchange.getRequest().getRemoteAddress() != null ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() : "unknown";
            String key = deviceId != null ? deviceId : clientIp;
            Bucket bucket = rateLimitBuckets.computeIfAbsent(key, k -> Bucket.builder().addLimit(limit).build());
            if (bucket.tryConsume(1)) {
                return chain.filter(exchange);
            }
            meterRegistry.counter("security.rate_limit.exceeded", "key", key).increment();
            return ResponseUtil.setErrorResponse(exchange, HttpStatus.TOO_MANY_REQUESTS, "rate_limit_exceeded", "Rate limit exceeded");
        };
    }

    private boolean validateCertificateAttributes(X509Certificate cert) {
        try {
            String cn = cert.getSubjectX500Principal().getName();
            return cn.contains("ezpay.client");
        } catch (Exception e) {
            LOGGER.warn("Certificate attribute validation failed: {}", e.getMessage());
            return false;
        }
    }
}
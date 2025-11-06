package com.ezpay.apigateway;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;

import java.time.Duration;

@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(ApiGatewayApplication.class);
		app.run(args);
	}

//	@Bean
//	public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
//		return builder.routes()
//				.route("user-service-register", r -> r.path("/api/register")
//						.filters(f -> f.stripPrefix(1)
//								.retry(rConfig -> rConfig.setRetries(3)
//										.setStatuses(HttpStatus.BAD_REQUEST ,HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.GATEWAY_TIMEOUT))
//								.circuitBreaker(cConfig -> cConfig.setName("userServiceCircuitBreaker")
//										.setFallbackUri("forward:/fallback")))
//						.uri("lb://user-service"))
//				.build();
//	}

	@Bean
	public CircuitBreakerConfig customCircuitBreakerConfig() {
		return CircuitBreakerConfig.custom()
				.failureRateThreshold(50)
				.waitDurationInOpenState(Duration.ofSeconds(10))
				.slidingWindowSize(10)
				.permittedNumberOfCallsInHalfOpenState(3)
				.build();
	}
}
package com.ezpay.apigateway.util;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class ResponseUtil {

    public static Mono<Void> setErrorResponse(ServerWebExchange exchange, HttpStatus status, String errorCode, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().add("X-Error-Code", errorCode);
        response.getHeaders().add("X-Error-Message", message);
        if (status == HttpStatus.UNAUTHORIZED) {
            response.getHeaders().add(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"" + errorCode + "\", error_description=\"" + message + "\"");
        }
        return response.setComplete();
    }

}

package com.ezpay.wallet.auth_service.util;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

public class ResponseUtil {

    public static void setErrorResponse(HttpServletResponse response, int status, String errorCode, String message) {
        response.setStatus(status);
        response.setHeader("X-Error-Code", errorCode);
        response.setHeader("X-Error-Message", message);
        if (status == 401) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"" + errorCode + "\", error_description=\"" + message + "\"");
        }
    }

    public static ResponseEntity<?> returnResponseEntity(HttpServletResponse response, int status, String errorCode, String message) {

        return ResponseEntity
                .status(status)
                .header("X-Error-Code", errorCode)
                .header("X-Error-Message", message)
                .body(Map.of(HttpHeaders.SERVER,"Bearer error=\"" + errorCode + "\", error_description=\"" + message + "\""));


    }

}

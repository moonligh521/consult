package com.moonligh.pay.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.moonligh.pay.domain.PayException;

@RestControllerAdvice
public class PayErrors {

    @ExceptionHandler(PayException.class)
    public ResponseEntity<Map<String, String>> handle(PayException error) {
        int status = switch (error.code()) {
            case "UNAUTHORIZED" -> 401;
            case "NOT_FOUND" -> 404;
            case "PAY_CHANNEL_NOT_READY", "PAYMENT_PENDING" -> 409;
            default -> 400;
        };
        return ResponseEntity.status(status).body(Map.of("code", error.code()));
    }
}

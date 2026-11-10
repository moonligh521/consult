package com.moonligh.benefit;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class BenefitErrors {

    @ExceptionHandler(BenefitException.class)
    public ResponseEntity<Map<String, String>> handle(BenefitException error) {
        int status = switch (error.code()) {
            case "NOT_FOUND" -> 404;
            case "CONFLICT" -> 409;
            default -> 400;
        };
        return ResponseEntity.status(status).body(Map.of("code", error.code()));
    }
}

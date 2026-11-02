package com.moonligh.consult.gateway;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.framework.Result;

import reactor.core.publisher.Mono;

/**
 * 网关自己拒绝请求时的统一返回：HTTP 状态 + Result JSON，前端按 code 提示。
 */
final class GatewayResponses {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GatewayResponses() {
    }

    static Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, ErrorCode code) {
        byte[] bytes;
        try {
            bytes = MAPPER.writeValueAsBytes(Result.fail(code));
        } catch (JsonProcessingException error) {
            bytes = ("{\"code\":" + code.getCode() + ",\"message\":\"" + code.getMessage() + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, "1");
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}

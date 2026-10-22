package com.moonligh.consult.framework;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 为每个请求设置 traceId：沿用请求头 {@code X-Trace-Id}，否则新建，并写入 MDC 与响应头。
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Trace-Id";
    /** 放进 MDC 的键名，和 logback 配置里的 %X{traceId} 对应。 */
    public static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // 调用方带了 X-Trace-Id 就沿用，方便全链路对日志。
        String traceId = request.getHeader(HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            // 线程可能被复用，请求结束必须清掉，否则下一个请求会串号。
            MDC.remove(MDC_KEY);
        }
    }
}

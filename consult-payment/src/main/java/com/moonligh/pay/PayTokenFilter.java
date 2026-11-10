package com.moonligh.pay;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 下单和查询认 X-Pay-Token。渠道回调不走这里。 */
@Component
@Order(1)
public class PayTokenFilter extends OncePerRequestFilter {

    private final PayProperties properties;

    public PayTokenFilter(PayProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/payments");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String expected = properties.getToken() == null ? "" : properties.getToken().trim();
        String given = request.getHeader("X-Pay-Token");
        byte[] left = expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = given == null ? new byte[0] : given.getBytes(StandardCharsets.UTF_8);
        if (left.length == 0 || left.length != right.length || !MessageDigest.isEqual(left, right)) {
            response.setStatus(401);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"UNAUTHORIZED\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}

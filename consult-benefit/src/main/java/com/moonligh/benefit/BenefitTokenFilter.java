package com.moonligh.benefit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 发卡、查账和预占都认 X-Benefit-Token。 */
@Component
@Order(1)
@EnableConfigurationProperties(BenefitProperties.class)
public class BenefitTokenFilter extends OncePerRequestFilter {

    private final BenefitProperties properties;

    public BenefitTokenFilter(BenefitProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/benefit");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String expected = properties.getToken() == null ? "" : properties.getToken().trim();
        String given = request.getHeader("X-Benefit-Token");
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

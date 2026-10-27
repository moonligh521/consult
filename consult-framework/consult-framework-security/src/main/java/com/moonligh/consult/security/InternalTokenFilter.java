package com.moonligh.consult.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.framework.Result;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * /internal/** 只认 X-Internal-Token，不走浏览器 JWT。
 */
public class InternalTokenFilter extends OncePerRequestFilter {

    private final byte[] expected;
    private final ObjectMapper mapper = new ObjectMapper();

    public InternalTokenFilter(String internalToken) {
        this.expected = internalToken == null ? new byte[0] : internalToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String given = request.getHeader(AuthTokens.INTERNAL_HEADER);
        byte[] actual = given == null ? new byte[0] : given.getBytes(StandardCharsets.UTF_8);
        if (expected.length == 0 || actual.length != expected.length || !MessageDigest.isEqual(expected, actual)) {
            response.setStatus(200);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getWriter(), Result.fail(ErrorCode.LOGIN_REQUIRED));
            return;
        }
        chain.doFilter(request, response);
    }
}

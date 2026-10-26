package com.moonligh.consult.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * HS256 登录令牌。密钥至少 32 字节。
 */
public final class AuthTokens {

    public static final String HEADER = "Authorization";
    public static final String ACCOUNT_HEADER = "X-Account-Id";
    public static final String BEARER = "Bearer ";

    private final byte[] secret;
    private final Duration ttl;

    public AuthTokens(String secret, Duration ttl) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("jwt secret 至少 32 字节");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttl = ttl == null ? Duration.ofDays(7) : ttl;
    }

    public String issue(String accountId, String mobile) {
        return issue(accountId, mobile, AuthPrincipal.PATIENT);
    }

    public String issue(String accountId, String mobile, String role) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(accountId)
                    .claim("mobile", mobile)
                    .claim("role", role == null || role.isBlank() ? AuthPrincipal.PATIENT : role)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(ttl)))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (JOSEException error) {
            throw new IllegalStateException("签发登录令牌失败", error);
        }
    }

    public long ttlSeconds() {
        return ttl.toSeconds();
    }

    public AuthPrincipal parse(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.LOGIN_REQUIRED);
        }
        String raw = token.startsWith(BEARER) ? token.substring(BEARER.length()).trim() : token.trim();
        try {
            SignedJWT jwt = SignedJWT.parse(raw);
            if (!jwt.verify(new MACVerifier(secret))) {
                throw new BusinessException(ErrorCode.LOGIN_REQUIRED);
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date exp = claims.getExpirationTime();
            if (exp == null || exp.toInstant().isBefore(Instant.now())) {
                throw new BusinessException(ErrorCode.LOGIN_REQUIRED);
            }
            String accountId = claims.getSubject();
            String mobile = claims.getStringClaim("mobile");
            String role = claims.getStringClaim("role");
            if (accountId == null || accountId.isBlank()) {
                throw new BusinessException(ErrorCode.LOGIN_REQUIRED);
            }
            return new AuthPrincipal(
                    accountId,
                    mobile,
                    role == null || role.isBlank() ? AuthPrincipal.PATIENT : role);
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new BusinessException(ErrorCode.LOGIN_REQUIRED);
        }
    }
}

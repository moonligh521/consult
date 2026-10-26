package com.moonligh.consult.identity.service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.identity.api.LoginRes;
import com.moonligh.consult.identity.config.AuthProperties;
import com.moonligh.consult.identity.domain.AccountRecord;
import com.moonligh.consult.identity.domain.Mobiles;
import com.moonligh.consult.identity.store.AccountStore;
import com.moonligh.consult.security.AuthTokens;

/**
 * 发验证码、登录开户、签发令牌。演示环境可用固定验证码，不走旧 HIS。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountStore accounts;
    private final StringRedisTemplate redis;
    private final AuthTokens tokens;
    private final AuthProperties properties;

    public AuthService(
            AccountStore accounts,
            StringRedisTemplate redis,
            AuthTokens tokens,
            AuthProperties properties) {
        this.accounts = accounts;
        this.redis = redis;
        this.tokens = tokens;
        this.properties = properties;
    }

    public void sendSms(String mobile) {
        String phone = Mobiles.require(mobile);
        String intervalKey = "consult:sms:interval:" + phone;
        Boolean first = redis.opsForValue().setIfAbsent(
                intervalKey, "1", properties.getSmsIntervalSeconds(), TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(first)) {
            throw new BusinessException(ErrorCode.SMS_TOO_FREQUENT);
        }
        String code = properties.getSmsDevCode() == null || properties.getSmsDevCode().isBlank()
                ? String.format("%06d", RANDOM.nextInt(1_000_000))
                : properties.getSmsDevCode().trim();
        redis.opsForValue().set("consult:sms:code:" + phone, code, properties.getSmsTtlSeconds(), TimeUnit.SECONDS);
        log.info("登录验证码已写入 mobile={}", phone);
    }

    public LoginRes login(String mobile, String captcha) {
        String phone = Mobiles.require(mobile);
        String expected = redis.opsForValue().get("consult:sms:code:" + phone);
        String input = captcha == null ? "" : captcha.trim();
        if (expected == null || expected.isBlank() || !expected.equals(input)) {
            throw new BusinessException(ErrorCode.INVALID_CAPTCHA);
        }
        redis.delete("consult:sms:code:" + phone);
        AccountRecord account = accounts.findByMobile(phone);
        if (account == null) {
            account = accounts.insert(phone);
        }
        if (!account.enabled()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        String token = tokens.issue(account.id(), account.mobile());
        return new LoginRes(token, account.id(), account.mobile(), tokens.ttlSeconds());
    }
}

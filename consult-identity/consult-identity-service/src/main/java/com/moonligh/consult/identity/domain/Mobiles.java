package com.moonligh.consult.identity.domain;

import java.util.regex.Pattern;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 登录手机号。
 */
public final class Mobiles {

    private static final Pattern CN = Pattern.compile("^1[3-9]\\d{9}$");

    private Mobiles() {
    }

    public static String require(String mobile) {
        String value = mobile == null ? "" : mobile.trim();
        if (!CN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.INVALID_MOBILE);
        }
        return value;
    }
}

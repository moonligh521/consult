package com.moonligh.consult.identity.api;

/**
 * 手机号 + 验证码登录。没有账号则自动开户。
 */
public record LoginReq(String mobile, String captcha) {
}

package com.moonligh.pay.domain;

/**
 * 验签通过后通知业务方。字段与挂号服务收到的付款通知一致。
 */
public record PaidNotice(String bizOrderNo, String amount, String channel) {
}

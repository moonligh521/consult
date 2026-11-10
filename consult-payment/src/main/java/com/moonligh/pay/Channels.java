package com.moonligh.pay;

/** 渠道名。银行和银联是同一个前台网关。 */
public final class Channels {

    private Channels() {
    }

    public static String normalize(String channel) {
        if (channel == null || channel.isBlank()) {
            return "";
        }
        String value = channel.trim().toLowerCase();
        return "unionpay".equals(value) ? "bank" : value;
    }

    public static boolean real(String channel) {
        String value = normalize(channel);
        return "alipay".equals(value) || "wechat".equals(value) || "bank".equals(value);
    }

    /** 微信商户单号最长 32，去掉连字符。支付宝和银联用原单号。 */
    public static String tradeNo(String channel, String bizOrderNo) {
        if (bizOrderNo == null) {
            return "";
        }
        return "wechat".equals(normalize(channel)) ? bizOrderNo.replace("-", "") : bizOrderNo;
    }
}

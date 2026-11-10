package com.moonligh.consult.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 挂号服务调用支付服务的地址。商户密钥在支付服务自己的配置里。
 */
@ConfigurationProperties(prefix = "consult.pay")
public class PayProperties {

    private String baseUrl = "http://127.0.0.1:18083";
    private String token = "";
    private String callbackUrl = "http://127.0.0.1:18081/api/pay/callback";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public boolean ready() {
        return filled(baseUrl) && filled(token) && filled(callbackUrl);
    }

    private static boolean filled(String value) {
        return value != null && !value.isBlank();
    }
}

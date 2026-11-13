package com.moonligh.consult.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 挂号服务调用权益服务的地址。卡种和额度在权益服务自己的库里。 */
@ConfigurationProperties(prefix = "consult.benefit")
public class BenefitProperties {

    private String baseUrl = "http://127.0.0.1:18084";
    private String token = "";

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

    public boolean ready() {
        return baseUrl != null && !baseUrl.isBlank() && token != null && !token.isBlank();
    }
}

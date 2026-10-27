package com.moonligh.consult.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 咨询服务调用身份服务的地址。内部令牌用 {@code consult.auth.internal-token}。
 */
@ConfigurationProperties(prefix = "consult.identity")
public class IdentityProperties {

    private String baseUrl = "http://127.0.0.1:18082";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}

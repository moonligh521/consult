package com.moonligh.consult.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 咨询服务核对就诊人时调用身份服务。
 */
@ConfigurationProperties(prefix = "consult.identity")
public class IdentityProperties {

    private String baseUrl = "http://127.0.0.1:18082";
    private String internalToken = "consult-internal";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }
}

package com.moonligh.consult.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关路由到的内部地址和 JWT 密钥。
 */
@ConfigurationProperties(prefix = "consult.gateway")
public class GatewayProperties {

    private String identityUri = "http://127.0.0.1:18082";
    private String agentUri = "http://127.0.0.1:18081";
    private String jwtSecret = "consult-dev-jwt-secret-key-32bytes!";
    private int jwtTtlHours = 168;

    public String getIdentityUri() {
        return identityUri;
    }

    public void setIdentityUri(String identityUri) {
        this.identityUri = identityUri;
    }

    public String getAgentUri() {
        return agentUri;
    }

    public void setAgentUri(String agentUri) {
        this.agentUri = agentUri;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public int getJwtTtlHours() {
        return jwtTtlHours;
    }

    public void setJwtTtlHours(int jwtTtlHours) {
        this.jwtTtlHours = jwtTtlHours;
    }
}

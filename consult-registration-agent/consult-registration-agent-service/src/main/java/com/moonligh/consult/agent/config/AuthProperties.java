package com.moonligh.consult.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 咨询服务侧 JWT。与身份服务、网关共用同一密钥。
 */
@ConfigurationProperties(prefix = "consult.auth")
public class AuthProperties {

    private String jwtSecret = "consult-dev-jwt-secret-key-32bytes!";
    private int jwtTtlHours = 168;

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

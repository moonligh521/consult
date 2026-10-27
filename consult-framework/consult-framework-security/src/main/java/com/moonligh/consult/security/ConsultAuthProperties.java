package com.moonligh.consult.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 各服务共用的 JWT 与内部调用令牌。短信、管理员账号仍留在身份服务。
 */
@ConfigurationProperties(prefix = "consult.auth")
public class ConsultAuthProperties {

    private String jwtSecret = "consult-dev-jwt-secret-key-32bytes!";
    private int jwtTtlHours = 168;
    private String internalToken = "consult-internal";

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

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }
}

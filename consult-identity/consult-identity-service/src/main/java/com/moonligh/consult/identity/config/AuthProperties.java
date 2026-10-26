package com.moonligh.consult.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 登录相关配置。密钥只从本机 yml 或环境变量读。
 */
@ConfigurationProperties(prefix = "consult.auth")
public class AuthProperties {

    private String jwtSecret = "consult-dev-jwt-secret-key-32bytes!";
    private int jwtTtlHours = 168;
    private int smsTtlSeconds = 300;
    private int smsIntervalSeconds = 60;
    /** 演示验证码。生产应留空，只发真实短信。 */
    private String smsDevCode = "000000";
    private String internalToken = "consult-internal";
    private String adminUsername = "admin";
    private String adminPassword = "admin1234";

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

    public int getSmsTtlSeconds() {
        return smsTtlSeconds;
    }

    public void setSmsTtlSeconds(int smsTtlSeconds) {
        this.smsTtlSeconds = smsTtlSeconds;
    }

    public int getSmsIntervalSeconds() {
        return smsIntervalSeconds;
    }

    public void setSmsIntervalSeconds(int smsIntervalSeconds) {
        this.smsIntervalSeconds = smsIntervalSeconds;
    }

    public String getSmsDevCode() {
        return smsDevCode;
    }

    public void setSmsDevCode(String smsDevCode) {
        this.smsDevCode = smsDevCode;
    }

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public void setAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }
}
